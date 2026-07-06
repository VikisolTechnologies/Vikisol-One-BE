package com.vikisol.one.hrtasks.service;

import com.vikisol.one.document.entity.Document;
import com.vikisol.one.document.repository.DocumentRepository;
import com.vikisol.one.employee.entity.BackgroundCheck;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.BackgroundCheckRepository;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.hrtasks.dto.HrTaskCenterResponse;
import com.vikisol.one.hrtasks.dto.HrTaskItem;
import com.vikisol.one.offboarding.entity.OffboardingChecklistItem;
import com.vikisol.one.offboarding.repository.OffboardingChecklistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Consolidates "things HR needs to act on today" from data that already exists across the BGV,
// Documents, Employee lifecycle, and Offboarding modules - deliberately just a set of read-only
// aggregation queries against existing repositories, not a new tracking table.
@Service
@RequiredArgsConstructor
public class HrTaskCenterService {

    // How far ahead "probation ending soon" looks.
    private static final int PROBATION_LOOKAHEAD_DAYS = 14;

    private final EmployeeRepository employeeRepository;
    private final BackgroundCheckRepository backgroundCheckRepository;
    private final DocumentRepository documentRepository;
    private final OffboardingChecklistItemRepository offboardingChecklistItemRepository;

    public HrTaskCenterResponse getHrTaskCenter() {
        List<HrTaskItem> bgvPending = getBgvPending();
        List<HrTaskItem> documentsPending = getDocumentsPending();
        List<HrTaskItem> joiningTomorrow = getJoiningTomorrow();
        List<HrTaskItem> probationEnding = getProbationEnding();
        List<HrTaskItem> confirmationDue = getConfirmationDue();
        List<HrTaskItem> resignationPending = getResignationPending();
        List<HrTaskItem> assetCollectionPending = getAssetCollectionPending();

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("bgvPending", bgvPending.size());
        counts.put("documentsPending", documentsPending.size());
        counts.put("joiningTomorrow", joiningTomorrow.size());
        counts.put("probationEnding", probationEnding.size());
        counts.put("confirmationDue", confirmationDue.size());
        counts.put("resignationPending", resignationPending.size());
        counts.put("assetCollectionPending", assetCollectionPending.size());
        counts.put("exitInterviewPending", 0);

        return HrTaskCenterResponse.builder()
                .counts(counts)
                .bgvPending(bgvPending)
                .documentsPending(documentsPending)
                .joiningTomorrow(joiningTomorrow)
                .probationEnding(probationEnding)
                .confirmationDue(confirmationDue)
                .resignationPending(resignationPending)
                .assetCollectionPending(assetCollectionPending)
                .exitInterviewPending(List.of())
                .exitInterviewDataAvailable(false)
                .build();
    }

    private List<HrTaskItem> getBgvPending() {
        List<BackgroundCheck> pending = backgroundCheckRepository.findPendingForActiveEmployees();
        return pending.stream()
                .map(bc -> toItem(bc.getEmployee(), bc.getCheckType().name().replace('_', ' ') + " verification pending (" + bc.getStatus() + ")", null))
                .collect(Collectors.toList());
    }

    private List<HrTaskItem> getDocumentsPending() {
        List<Document> pending = documentRepository.findPendingVerificationForActiveEmployees();
        return pending.stream()
                .map(d -> toItem(d.getEmployee(), d.getType().name().replace('_', ' ') + " pending verification", null))
                .collect(Collectors.toList());
    }

    private List<HrTaskItem> getJoiningTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Employee> joining = employeeRepository.findByIsActiveTrueAndDateOfJoining(tomorrow);
        return joining.stream()
                .map(e -> toItem(e, "Joining tomorrow", e.getDateOfJoining()))
                .collect(Collectors.toList());
    }

    private List<HrTaskItem> getProbationEnding() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(PROBATION_LOOKAHEAD_DAYS);
        List<Employee> ending = employeeRepository.findByIsActiveTrueAndLifecycleStatusAndProbationEndDateBetween(
                Employee.LifecycleStatus.PROBATION, today, until);
        return ending.stream()
                .map(e -> toItem(e, "Probation ending " + e.getProbationEndDate(), e.getProbationEndDate()))
                .collect(Collectors.toList());
    }

    private List<HrTaskItem> getConfirmationDue() {
        LocalDate today = LocalDate.now();
        List<Employee> overdue = employeeRepository.findByIsActiveTrueAndLifecycleStatusAndProbationEndDateBefore(
                Employee.LifecycleStatus.PROBATION, today);
        return overdue.stream()
                .map(e -> toItem(e, "Probation ended " + e.getProbationEndDate() + " - confirmation pending", e.getProbationEndDate()))
                .collect(Collectors.toList());
    }

    private List<HrTaskItem> getResignationPending() {
        List<Employee> onNotice = employeeRepository.findByIsActiveTrueAndLifecycleStatus(Employee.LifecycleStatus.NOTICE_PERIOD);
        return onNotice.stream()
                .map(e -> toItem(e, "Resignation in notice period", null))
                .collect(Collectors.toList());
    }

    private List<HrTaskItem> getAssetCollectionPending() {
        List<OffboardingChecklistItem> items = offboardingChecklistItemRepository.findPendingItAssetItems();
        return items.stream()
                .map(i -> toItem(i.getOffboardingCase().getEmployee(), "Asset pending: " + i.getLabel(), null))
                .collect(Collectors.toList());
    }

    private HrTaskItem toItem(Employee e, String context, LocalDate date) {
        return HrTaskItem.builder()
                .employeeId(e.getId())
                .employeeCode(e.getEmployeeId())
                .employeeName(e.getFirstName() + " " + e.getLastName())
                .context(context)
                .date(date != null ? date.toString() : null)
                .build();
    }
}
