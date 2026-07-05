package com.vikisol.one.employee.service;

import com.vikisol.one.audit.entity.AuditLog;
import com.vikisol.one.audit.repository.AuditLogRepository;
import com.vikisol.one.employee.dto.EmployeeTimelineEntry;
import com.vikisol.one.employee.entity.BackgroundCheck;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.BackgroundCheckRepository;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.offboarding.entity.OffboardingCase;
import com.vikisol.one.offboarding.entity.OffboardingStageHistory;
import com.vikisol.one.offboarding.repository.OffboardingCaseRepository;
import com.vikisol.one.offboarding.repository.OffboardingStageHistoryRepository;
import com.vikisol.one.recruitment.dto.CandidateTimelineEntry;
import com.vikisol.one.recruitment.repository.CandidateRepository;
import com.vikisol.one.recruitment.service.RecruitmentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Aggregates a read-only chronological feed of an employee's significant lifecycle events out of
// data that already lives elsewhere (recruitment/candidate history, BGV, offboarding stage
// history, general audit log) - no new event-storage table. Kept as its own service (rather than
// folded into EmployeeService) so it can depend on RecruitmentService without creating a circular
// bean dependency, since RecruitmentService already depends on EmployeeService.
@Service
@RequiredArgsConstructor
public class EmployeeTimelineService {

    private final EmployeeRepository employeeRepository;
    private final CandidateRepository candidateRepository;
    private final RecruitmentService recruitmentService;
    private final BackgroundCheckRepository backgroundCheckRepository;
    private final OffboardingCaseRepository offboardingCaseRepository;
    private final OffboardingStageHistoryRepository offboardingStageHistoryRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<EmployeeTimelineEntry> getTimeline(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        List<EmployeeTimelineEntry> entries = new ArrayList<>();

        // Recruitment history - only if this employee was converted from a tracked candidate.
        candidateRepository.findByConvertedEmployeeId(employee.getEmployeeId()).ifPresent(candidate -> {
            for (CandidateTimelineEntry e : recruitmentService.getCandidateTimeline(candidate.getId())) {
                entries.add(new EmployeeTimelineEntry(e.timestamp(), "RECRUITMENT", e.title(), e.detail()));
            }
        });

        // BGV completion events.
        for (BackgroundCheck check : backgroundCheckRepository.findByEmployeeId(employee.getId())) {
            if (check.getReviewedAt() != null) {
                entries.add(new EmployeeTimelineEntry(check.getReviewedAt(), "BGV",
                        check.getCheckType() + " Check " + check.getStatus(), check.getRemarks()));
            }
        }

        // Onboarding completion - the checklist is tracked as flat booleans with no per-item
        // timestamp, so the best available signal is induction completion, timestamped with the
        // employee record's own last-updated time.
        if (employee.isOnboardingInductionCompleted()) {
            entries.add(new EmployeeTimelineEntry(employee.getUpdatedAt(), "ONBOARDING",
                    "Onboarding Induction Completed", null));
        }

        // Offboarding stage history (across any offboarding case(s) for this employee).
        for (OffboardingCase offboardingCase : offboardingCaseRepository.findByEmployeeId(employee.getId())) {
            for (OffboardingStageHistory history : offboardingStageHistoryRepository
                    .findByOffboardingCaseIdOrderByChangedAtAsc(offboardingCase.getId())) {
                String title = history.getToStage() != null
                        ? "Offboarding: " + history.getToStage().name().replace('_', ' ')
                        : "Offboarding Update";
                entries.add(new EmployeeTimelineEntry(history.getChangedAt(), "OFFBOARDING", title, history.getComments()));
            }
        }

        // General audit trail entries scoped to this employee (target == employeeId business code).
        for (AuditLog log : auditLogRepository.findByTargetOrderByTimestampDesc(employee.getEmployeeId())) {
            entries.add(new EmployeeTimelineEntry(log.getTimestamp(), "AUDIT", log.getAction(), log.getDetails()));
        }

        return entries.stream()
                .sorted(Comparator.comparing(EmployeeTimelineEntry::timestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }
}
