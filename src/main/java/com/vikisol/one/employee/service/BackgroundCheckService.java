package com.vikisol.one.employee.service;

import com.vikisol.one.employee.dto.BackgroundCheckResponse;
import com.vikisol.one.employee.dto.BackgroundCheckUpdateRequest;
import com.vikisol.one.employee.entity.BackgroundCheck;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.BackgroundCheckRepository;
import com.vikisol.one.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Background Verification (BGV) is intentionally its own domain, not folded into the onboarding
// checklist booleans - it needs a real per-check-type workflow (Pending -> Submitted -> In
// Review -> Approved/Rejected) with a reviewer and remarks trail, and it's HR/CEO/Admin-only to
// update regardless of who can update other onboarding data.
@Service
@RequiredArgsConstructor
public class BackgroundCheckService {

    private final BackgroundCheckRepository backgroundCheckRepository;
    private final EmployeeRepository employeeRepository;

    // Every employee should have all 8 check rows visible from day one (even before HR has
    // started reviewing any of them) rather than only showing rows once someone creates them -
    // lazily creates any missing ones on first read instead of a bulk migration/seed step.
    @Transactional
    public List<BackgroundCheckResponse> getForEmployee(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        List<BackgroundCheck> existing = backgroundCheckRepository.findByEmployeeId(employeeId);
        List<BackgroundCheck.CheckType> existingTypes = existing.stream().map(BackgroundCheck::getCheckType).toList();

        List<BackgroundCheck> missing = Arrays.stream(BackgroundCheck.CheckType.values())
                .filter(t -> !existingTypes.contains(t))
                .map(t -> BackgroundCheck.builder().employee(employee).checkType(t).status(BackgroundCheck.Status.PENDING).build())
                .toList();
        if (!missing.isEmpty()) {
            existing = new java.util.ArrayList<>(existing);
            existing.addAll(backgroundCheckRepository.saveAll(missing));
        }

        return existing.stream()
                .sorted((a, b) -> a.getCheckType().compareTo(b.getCheckType()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BackgroundCheckResponse updateStatus(UUID checkId, BackgroundCheckUpdateRequest request, UUID reviewerEmployeeId) {
        BackgroundCheck check = backgroundCheckRepository.findById(checkId)
                .orElseThrow(() -> new EntityNotFoundException("Background check not found"));
        if (request.status() != null) check.setStatus(request.status());
        if (request.remarks() != null) check.setRemarks(request.remarks());
        check.setReviewedById(reviewerEmployeeId);
        check.setReviewedAt(LocalDateTime.now());
        return toResponse(backgroundCheckRepository.save(check));
    }

    // Overall completion for dashboard widgets ("Employees Pending BGV") - a resolved fraction so
    // HR sees at a glance how far along an employee's full BGV is, not just item-by-item.
    @Transactional(readOnly = true)
    public boolean isFullyApproved(UUID employeeId) {
        List<BackgroundCheck> checks = backgroundCheckRepository.findByEmployeeId(employeeId);
        return !checks.isEmpty() && checks.stream().allMatch(c -> c.getStatus() == BackgroundCheck.Status.APPROVED);
    }

    private BackgroundCheckResponse toResponse(BackgroundCheck c) {
        String reviewerName = c.getReviewedById() != null
                ? employeeRepository.findById(c.getReviewedById()).map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null)
                : null;
        return new BackgroundCheckResponse(c.getId(), c.getCheckType(), c.getStatus(), c.getRemarks(), reviewerName, c.getReviewedAt());
    }
}
