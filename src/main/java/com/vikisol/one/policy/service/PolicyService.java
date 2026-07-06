package com.vikisol.one.policy.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.policy.dto.PolicyRequest;
import com.vikisol.one.policy.dto.PolicyResponse;
import com.vikisol.one.policy.entity.CompanyPolicy;
import com.vikisol.one.policy.repository.CompanyPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// CRUD for company policy documents. Reading is open to every authenticated role (everyone needs
// to see/acknowledge policies); creating/updating/disabling is CEO/HR Manager/Admin only, enforced
// at the controller via @PreAuthorize.
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final CompanyPolicyRepository policyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PolicyResponse> list(boolean includeInactive) {
        List<CompanyPolicy> policies = includeInactive
                ? policyRepository.findAllByOrderByCategoryAscTitleAsc()
                : policyRepository.findByActiveTrueOrderByCategoryAscTitleAsc();
        return policies.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PolicyResponse getById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public CompanyPolicy getEntity(UUID id) {
        return policyRepository.findById(id).orElseThrow(() -> new RuntimeException("Policy not found"));
    }

    @Transactional
    public PolicyResponse create(PolicyRequest request, String createdByEmail) {
        CompanyPolicy policy = CompanyPolicy.builder()
                .title(request.title())
                .category(request.category())
                .content(request.content())
                .version(request.version() != null ? request.version() : "1.0")
                .effectiveDate(request.effectiveDate())
                .active(request.active() == null || request.active())
                .requiresAcknowledgement(request.requiresAcknowledgement() == null || request.requiresAcknowledgement())
                .createdByEmail(createdByEmail)
                .build();
        policy = policyRepository.save(policy);
        auditService.record("Company Policy Created", policy.getTitle(), "Category: " + policy.getCategory());
        return toResponse(policy);
    }

    @Transactional
    public PolicyResponse update(UUID id, PolicyRequest request) {
        CompanyPolicy policy = getEntity(id);
        policy.setTitle(request.title());
        policy.setCategory(request.category());
        policy.setContent(request.content());
        if (request.version() != null) policy.setVersion(request.version());
        policy.setEffectiveDate(request.effectiveDate());
        if (request.active() != null) policy.setActive(request.active());
        if (request.requiresAcknowledgement() != null) policy.setRequiresAcknowledgement(request.requiresAcknowledgement());
        policy = policyRepository.save(policy);
        auditService.record("Company Policy Updated", policy.getTitle(), "Version: " + policy.getVersion());
        return toResponse(policy);
    }

    // Soft-disable rather than a hard delete - policies may be referenced by existing
    // acknowledgement records and compliance history shouldn't disappear just because a policy was
    // superseded; disabling just hides it from the active list/sidebar going forward.
    @Transactional
    public void disable(UUID id) {
        CompanyPolicy policy = getEntity(id);
        policy.setActive(false);
        policyRepository.save(policy);
        auditService.record("Company Policy Disabled", policy.getTitle(), "Soft-disabled (active=false)");
    }

    private PolicyResponse toResponse(CompanyPolicy p) {
        return new PolicyResponse(
                p.getId(), p.getTitle(), p.getCategory(), p.getContent(), p.getVersion(), p.getEffectiveDate(),
                p.isActive(), p.isRequiresAcknowledgement(), p.getCreatedByEmail(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
