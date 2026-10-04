package com.gymmanager.service;

/**
 * Storage backend for member document bytes, abstracted away from
 * {@link MemberDocumentService} so the provider can be swapped (e.g. Postgres BLOB today,
 * an object store like R2/S3 later) via {@code app.storage.provider} without touching the
 * upload/download/delete flow.
 */
public interface DocumentStorageService {

    void store(String key, byte[] content, String contentType);

    byte[] load(String key);

    void delete(String key);
}
