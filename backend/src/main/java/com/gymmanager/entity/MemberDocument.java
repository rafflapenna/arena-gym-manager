package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Metadata row for a document attached to a {@link Member} (medical certificate or a
 * generic attachment). The file bytes live separately in {@link MemberDocumentBlob},
 * keyed by {@link #storageKey} — kept unidirectional (no {@code @OneToMany} back on
 * {@code Member}) the same way {@link CheckIn} references {@code Member}.
 */
@Entity
@Table(name = "member_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    /** Free title. Optional for MEDICAL_CERT, required for OTHER (enforced in the service). */
    @Column(length = 150)
    private String title;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** Opaque key into the storage backend (see {@link com.gymmanager.service.DocumentStorageService}). */
    @Column(name = "storage_key", nullable = false, unique = true, length = 100)
    private String storageKey;

    /** MEDICAL_CERT only; ignored (kept null) for OTHER. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /** AppUser id of the operator (or member, for self-service) who uploaded the file. */
    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
