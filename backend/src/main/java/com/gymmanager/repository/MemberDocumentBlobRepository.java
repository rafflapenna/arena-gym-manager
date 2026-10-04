package com.gymmanager.repository;

import com.gymmanager.entity.MemberDocumentBlob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDocumentBlobRepository extends JpaRepository<MemberDocumentBlob, String> {
}
