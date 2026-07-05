package com.vikisol.one.audit.repository;

import com.vikisol.one.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    // `target` stores the business employeeId string (e.g. "VIK-0008") for employee-related audit
    // entries - used to scope the Employee Timeline to just this employee's own events.
    List<AuditLog> findByTargetOrderByTimestampDesc(String target);
}
