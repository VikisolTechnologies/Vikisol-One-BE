package com.vikisol.one.policy.repository;

import com.vikisol.one.policy.entity.PolicyAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyAcknowledgementRepository extends JpaRepository<PolicyAcknowledgement, UUID> {
    Optional<PolicyAcknowledgement> findByPolicyIdAndEmployeeId(UUID policyId, UUID employeeId);
    List<PolicyAcknowledgement> findByPolicyId(UUID policyId);
    List<PolicyAcknowledgement> findByEmployeeId(UUID employeeId);
}
