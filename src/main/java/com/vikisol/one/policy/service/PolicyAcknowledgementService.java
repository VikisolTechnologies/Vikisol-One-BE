package com.vikisol.one.policy.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.policy.dto.AcknowledgementStatusResponse;
import com.vikisol.one.policy.dto.PolicyAcknowledgementRecord;
import com.vikisol.one.policy.entity.CompanyPolicy;
import com.vikisol.one.policy.entity.PolicyAcknowledgement;
import com.vikisol.one.policy.repository.PolicyAcknowledgementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Tracks the View -> Accept flow per (policy, employee) pair. A view is logged every time (not
// audited - too noisy per the acceptance-only audit requirement); acceptance is a one-time,
// audited compliance event that also captures a typed digital signature and (best-effort) IP.
@Service
@RequiredArgsConstructor
public class PolicyAcknowledgementService {

    private final PolicyAcknowledgementRepository acknowledgementRepository;
    private final PolicyService policyService;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    @Transactional
    public void recordView(UUID policyId, UUID employeeId) {
        PolicyAcknowledgement ack = acknowledgementRepository.findByPolicyIdAndEmployeeId(policyId, employeeId)
                .orElseGet(() -> PolicyAcknowledgement.builder().policyId(policyId).employeeId(employeeId).build());
        // Don't overwrite an already-recorded first view (or a later acceptance) with a re-open.
        if (ack.getViewedAt() == null) {
            ack.setViewedAt(LocalDateTime.now());
        }
        acknowledgementRepository.save(ack);
    }

    @Transactional
    public AcknowledgementStatusResponse recordAcceptance(UUID policyId, UUID employeeId, String signatureText, String ipAddress) {
        if (signatureText == null || signatureText.isBlank()) {
            throw new RuntimeException("A typed signature is required to accept this policy");
        }
        CompanyPolicy policy = policyService.getEntity(policyId);
        PolicyAcknowledgement ack = acknowledgementRepository.findByPolicyIdAndEmployeeId(policyId, employeeId)
                .orElseGet(() -> PolicyAcknowledgement.builder().policyId(policyId).employeeId(employeeId).build());
        LocalDateTime now = LocalDateTime.now();
        if (ack.getViewedAt() == null) ack.setViewedAt(now);
        ack.setAcceptedAt(now);
        ack.setDigitalSignatureText(signatureText);
        ack.setIpAddress(ipAddress);
        ack = acknowledgementRepository.save(ack);

        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        String employeeLabel = employee != null ? employee.getFirstName() + " " + employee.getLastName() : employeeId.toString();
        auditService.record("Policy Acknowledged", policy.getTitle(), employeeLabel + " signed as \"" + signatureText + "\"");

        return toStatus(ack);
    }

    @Transactional(readOnly = true)
    public AcknowledgementStatusResponse getStatus(UUID policyId, UUID employeeId) {
        return acknowledgementRepository.findByPolicyIdAndEmployeeId(policyId, employeeId)
                .map(this::toStatus)
                .orElse(new AcknowledgementStatusResponse("NOT_VIEWED", null, null));
    }

    // HR/CEO compliance view - every active employee's status for this policy, including those
    // who haven't viewed it at all (no acknowledgement row yet).
    @Transactional(readOnly = true)
    public List<PolicyAcknowledgementRecord> listForPolicy(UUID policyId) {
        Map<UUID, PolicyAcknowledgement> byEmployee = acknowledgementRepository.findByPolicyId(policyId).stream()
                .collect(Collectors.toMap(PolicyAcknowledgement::getEmployeeId, a -> a));

        List<Employee> employees = employeeRepository.findByIsActiveTrue(Pageable.unpaged()).getContent();

        return employees.stream().map(e -> {
            PolicyAcknowledgement ack = byEmployee.get(e.getId());
            String status = ack == null ? "NOT_VIEWED" : (ack.getAcceptedAt() != null ? "ACCEPTED" : "VIEWED");
            return new PolicyAcknowledgementRecord(
                    e.getId(),
                    e.getFirstName() + " " + e.getLastName(),
                    e.getEmployeeId(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null,
                    status,
                    ack != null ? ack.getViewedAt() : null,
                    ack != null ? ack.getAcceptedAt() : null
            );
        }).toList();
    }

    private AcknowledgementStatusResponse toStatus(PolicyAcknowledgement ack) {
        String status = ack.getAcceptedAt() != null ? "ACCEPTED" : (ack.getViewedAt() != null ? "VIEWED" : "NOT_VIEWED");
        return new AcknowledgementStatusResponse(status, ack.getViewedAt(), ack.getAcceptedAt());
    }
}
