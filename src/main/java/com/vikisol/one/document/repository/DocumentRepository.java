package com.vikisol.one.document.repository;

import com.vikisol.one.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByEmployeeId(UUID employeeId);

    List<Document> findByEmployeeIdAndType(UUID employeeId, Document.DocumentType type);

    List<Document> findByIsVerifiedFalse();

    // Used by the HR Task Center's "Documents Pending" category - unverified documents belonging
    // to currently active employees only (skips exited/inactive employees' stale document rows).
    @Query("SELECT d FROM Document d WHERE d.isVerified = false AND d.isActive = true AND d.employee.isActive = true")
    List<Document> findPendingVerificationForActiveEmployees();
}
