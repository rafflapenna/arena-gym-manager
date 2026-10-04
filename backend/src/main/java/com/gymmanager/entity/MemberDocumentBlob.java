package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * File bytes for a {@link MemberDocument}, in a separate table so listing/metadata queries
 * never drag the payload along. Keyed by the document's opaque {@code storageKey} rather
 * than its id so the storage backend can be swapped (e.g. for R2/S3) without touching this
 * key — see {@link com.gymmanager.service.DocumentStorageService}.
 */
@Entity
@Table(name = "member_document_blobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDocumentBlob {

    @Id
    @Column(name = "storage_key", length = 100)
    private String storageKey;

    // Deliberately NOT @Lob: on PostgreSQL, Hibernate maps a @Lob byte[] to a large object
    // (oid) rather than a plain column, which needs extra housekeeping (orphaned large
    // objects aren't cleaned up by a normal DELETE). A plain byte[] field maps to bytea,
    // which is simpler and plenty for document-sized payloads (10MB cap, see application.yml).
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;
}
