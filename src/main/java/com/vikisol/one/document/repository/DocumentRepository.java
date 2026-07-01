package com.vikisol.one.document.repository;

import com.vikisol.one.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByEmployeeId(UUID employeeId);

    List<Document> findByEmployeeIdAndType(UUID employeeId, Document.DocumentType type);

    List<Document> findByIsVerifiedFalse();
}
