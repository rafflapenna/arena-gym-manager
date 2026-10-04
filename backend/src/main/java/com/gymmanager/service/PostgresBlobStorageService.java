package com.gymmanager.service;

import com.gymmanager.entity.MemberDocumentBlob;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberDocumentBlobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link DocumentStorageService}: stores the file bytes as a Postgres BLOB
 * (see {@link MemberDocumentBlob}). Active unless {@code app.storage.provider} is set to
 * something else.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "postgres-blob", matchIfMissing = true)
public class PostgresBlobStorageService implements DocumentStorageService {

    private final MemberDocumentBlobRepository blobRepository;

    @Override
    @Transactional
    public void store(String key, byte[] content, String contentType) {
        blobRepository.save(MemberDocumentBlob.builder()
                .storageKey(key)
                .content(content)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] load(String key) {
        return blobRepository.findById(key)
                .map(MemberDocumentBlob::getContent)
                .orElseThrow(() -> new ResourceNotFoundException("Document blob not found for key: " + key));
    }

    @Override
    @Transactional
    public void delete(String key) {
        blobRepository.deleteById(key);
    }
}
