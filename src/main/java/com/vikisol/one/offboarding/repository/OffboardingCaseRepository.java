package com.vikisol.one.offboarding.repository;

import com.vikisol.one.offboarding.entity.OffboardingCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OffboardingCaseRepository extends JpaRepository<OffboardingCase, UUID> {

    Optional<OffboardingCase> findTopByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<OffboardingCase> findByEmployeeId(UUID employeeId);

    List<OffboardingCase> findByStatus(OffboardingCase.CaseStatus status);

    List<OffboardingCase> findByEmployeeReportingManagerId(UUID managerId);
}
