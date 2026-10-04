package com.gymmanager.controller;

import com.gymmanager.dto.MedicalCertExpiringDTO;
import com.gymmanager.dto.MemberDocumentResponseDTO;
import com.gymmanager.entity.DocumentType;
import com.gymmanager.service.MemberDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Documents attached to a member (medical certificates + generic attachments).
 * Member self-service (own documents only) lives on {@code MeController} under
 * {@code /me/documents}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemberDocumentController {

    private final MemberDocumentService memberDocumentService;

    @PostMapping(value = "/members/{memberId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MemberDocumentResponseDTO> upload(
            @PathVariable Long memberId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") DocumentType type,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "expiryDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate
    ) {
        MemberDocumentResponseDTO dto = memberDocumentService.upload(memberId, type, title, expiryDate, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/members/{memberId}/documents")
    public List<MemberDocumentResponseDTO> findByMember(@PathVariable Long memberId) {
        return memberDocumentService.findByMember(memberId);
    }

    @GetMapping("/members/{memberId}/documents/{docId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long memberId, @PathVariable Long docId) {
        return downloadResponse(memberDocumentService.download(memberId, docId));
    }

    @DeleteMapping("/members/{memberId}/documents/{docId}")
    public ResponseEntity<Void> delete(@PathVariable Long memberId, @PathVariable Long docId) {
        memberDocumentService.delete(memberId, docId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/expiring")
    public List<MedicalCertExpiringDTO> findExpiring(@RequestParam(defaultValue = "30") int days) {
        return memberDocumentService.findExpiring(days);
    }

    static ResponseEntity<ByteArrayResource> downloadResponse(MemberDocumentService.Download download) {
        String contentType = download.contentType() != null ? download.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(new ByteArrayResource(download.content()));
    }
}
