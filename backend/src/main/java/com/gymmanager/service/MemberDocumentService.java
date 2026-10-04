package com.gymmanager.service;

import com.gymmanager.dto.MedicalCertExpiringDTO;
import com.gymmanager.dto.MemberDocumentResponseDTO;
import com.gymmanager.entity.DocumentType;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.MemberDocument;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberDocumentRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Documents attached to a {@link Member}: medical certificates (enriched with an
 * {@code expiryDate}, feeding {@link MedicalCertReminderJob}) and generic, unlimited,
 * free-title attachments ({@code OTHER}).
 *
 * <p>Scoping mirrors {@code CheckInService}/{@code CheckinTokenService}: an unknown member
 * id or one belonging to another gym surfaces as a 404 (anti-IDOR). A document is further
 * scoped to its own member via {@code findByIdAndMemberId} — a document belonging to a
 * different member of the same gym also 404s.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberDocumentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int KEY_BYTES = 24; // 192 bits — unique, unguessable storage key

    private final MemberDocumentRepository memberDocumentRepository;
    private final MemberRepository memberRepository;
    private final DocumentStorageService storage;
    private final CurrentUser currentUser;

    @Transactional
    public MemberDocumentResponseDTO upload(
            Long memberId, DocumentType type, String title, LocalDate expiryDate, MultipartFile file
    ) {
        Member member = getMemberInScope(memberId);
        validateFile(file);

        String resolvedTitle = title;
        LocalDate resolvedExpiryDate = null;
        if (type == DocumentType.MEDICAL_CERT) {
            resolvedExpiryDate = expiryDate;
        } else if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "title is required for OTHER documents");
        }

        byte[] content = readBytes(file);
        String storageKey = generateStorageKey();
        storage.store(storageKey, content, file.getContentType());

        MemberDocument document = MemberDocument.builder()
                .member(member)
                .type(type)
                .title(resolvedTitle)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes((long) content.length)
                .storageKey(storageKey)
                .expiryDate(resolvedExpiryDate)
                .uploadedBy(currentUser.get().userId())
                .build();

        MemberDocument saved;
        try {
            saved = memberDocumentRepository.save(document);
        } catch (RuntimeException e) {
            // The metadata row and the blob are two separate stores (Postgres BLOB today,
            // possibly R2/S3 later): a transaction rollback here would NOT undo storage.store,
            // so the compensation is explicit rather than relied upon.
            storage.delete(storageKey);
            throw e;
        }
        log.info("Uploaded document id={} memberId={} type={}", saved.getId(), memberId, type);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<MemberDocumentResponseDTO> findByMember(Long memberId) {
        getMemberInScope(memberId);
        return memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(memberId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public Download download(Long memberId, Long documentId) {
        getMemberInScope(memberId);
        MemberDocument document = getDocumentInScope(memberId, documentId);
        byte[] content = storage.load(document.getStorageKey());
        return new Download(content, document.getContentType(), document.getOriginalFilename());
    }

    @Transactional
    public void delete(Long memberId, Long documentId) {
        getMemberInScope(memberId);
        MemberDocument document = getDocumentInScope(memberId, documentId);
        storage.delete(document.getStorageKey());
        memberDocumentRepository.delete(document);
        log.info("Deleted document id={} memberId={}", documentId, memberId);
    }

    /** MEDICAL_CERT documents expiring within {@code days}, scoped to the caller's gym. */
    @Transactional(readOnly = true)
    public List<MedicalCertExpiringDTO> findExpiring(int days) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(days);
        Long gymId = currentUser.isAdmin() ? null : currentUser.gymId();
        return memberDocumentRepository.findExpiringMedicalCerts(today, threshold, gymId)
                .stream()
                .map(d -> MedicalCertExpiringDTO.builder()
                        .documentId(d.getId())
                        .memberId(d.getMember().getId())
                        .memberFullName(d.getMember().getPersonalInfo().getFirstName() + " "
                                + d.getMember().getPersonalInfo().getLastName())
                        .expiryDate(d.getExpiryDate())
                        .daysUntilExpiry(ChronoUnit.DAYS.between(today, d.getExpiryDate()))
                        .build())
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported file type: " + contentType);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }

    private String generateStorageKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private Member getMemberInScope(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        Long gymId = member.getGym() != null ? member.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        return member;
    }

    private MemberDocument getDocumentInScope(Long memberId, Long documentId) {
        return memberDocumentRepository.findByIdAndMemberId(documentId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private MemberDocumentResponseDTO toDTO(MemberDocument d) {
        return MemberDocumentResponseDTO.builder()
                .id(d.getId())
                .memberId(d.getMember().getId())
                .type(d.getType().name())
                .title(d.getTitle())
                .originalFilename(d.getOriginalFilename())
                .contentType(d.getContentType())
                .sizeBytes(d.getSizeBytes())
                .expiryDate(d.getExpiryDate())
                .uploadedAt(d.getUploadedAt())
                .build();
    }

    /** Downloaded file payload, plus the metadata the controller needs for the response headers. */
    public record Download(byte[] content, String contentType, String filename) {}
}
