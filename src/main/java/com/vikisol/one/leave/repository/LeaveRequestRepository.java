package com.vikisol.one.leave.repository;

import com.vikisol.one.leave.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    Page<LeaveRequest> findByEmployeeId(UUID employeeId, Pageable pageable);

    List<LeaveRequest> findByEmployeeIdAndStatus(UUID employeeId, LeaveRequest.LeaveStatus status);

    Page<LeaveRequest> findByStatus(LeaveRequest.LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByApprovedById(UUID approvedById, Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.reportingManagerId = :managerId AND lr.status = 'PENDING'")
    Page<LeaveRequest> findPendingForApprover(@Param("managerId") UUID managerId, Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.reportingManagerId = :managerId")
    Page<LeaveRequest> findByEmployeeReportingManagerId(@Param("managerId") UUID managerId, Pageable pageable);
}
