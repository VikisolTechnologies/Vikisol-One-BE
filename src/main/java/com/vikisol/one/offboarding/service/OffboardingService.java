package com.vikisol.one.offboarding.service;

import com.vikisol.one.asset.dto.AssetAssignmentResponse;
import com.vikisol.one.asset.service.AssetService;
import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.document.repository.DocumentRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.notification.entity.Notification.NotificationType;
import com.vikisol.one.notification.service.NotificationService;
import com.vikisol.one.offboarding.dto.*;
import com.vikisol.one.offboarding.entity.OffboardingCase;
import com.vikisol.one.offboarding.entity.OffboardingCase.Stage;
import com.vikisol.one.offboarding.entity.OffboardingChecklistItem;
import com.vikisol.one.offboarding.entity.OffboardingStageHistory;
import com.vikisol.one.offboarding.repository.OffboardingCaseRepository;
import com.vikisol.one.offboarding.repository.OffboardingChecklistItemRepository;
import com.vikisol.one.offboarding.repository.OffboardingStageHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// The offboarding module - full exit lifecycle for an employee, from resignation/termination
// through a fixed stage pipeline to a completed exit with generated documents emailed to the
// employee's personal address. Mirrors the BGV domain's shape (dedicated entity + checklist +
// history rather than flat booleans) since offboarding needs the same kind of per-item and
// per-stage workflow trail.
@Service
@RequiredArgsConstructor
@Slf4j
public class OffboardingService {

    private final OffboardingCaseRepository caseRepository;
    private final OffboardingChecklistItemRepository checklistRepository;
    private final OffboardingStageHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AssetService assetService;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // Fixed forward order of the pipeline - BGV_EXIT_VERIFICATION is skipped for cases where
    // bgvRequired is false (see advanceStage / nextStage).
    private static final List<Stage> ORDER = List.of(
            Stage.RESIGNATION_SUBMITTED, Stage.MANAGER_REVIEW, Stage.HR_REVIEW, Stage.KNOWLEDGE_TRANSFER,
            Stage.ASSET_COLLECTION, Stage.IT_CLEARANCE, Stage.FINANCE_CLEARANCE, Stage.BGV_EXIT_VERIFICATION,
            Stage.FINAL_HR_APPROVAL, Stage.EXIT_DOCS_GENERATED, Stage.COMPLETED);

    private static final Map<OffboardingChecklistItem.Category, List<String>> DEFAULT_CHECKLIST = new LinkedHashMap<>() {{
        put(OffboardingChecklistItem.Category.HR, List.of(
                "Exit Interview", "Final Settlement", "Leave Balance", "PF", "Gratuity", "Experience Letter"));
        // Physical-asset items ("Laptop Returned", "Charger Returned", "ID Card Returned", etc.)
        // are no longer generic placeholders - seedChecklist() generates one dynamic item per
        // asset actually assigned to the employee instead. Only the access/account items (which
        // aren't tied to a specific physical asset) stay as fixed defaults here.
        put(OffboardingChecklistItem.Category.IT, List.of(
                "VPN Disabled", "Email Disabled", "GitHub Access Removed", "Jira Access Removed", "Slack Removed"));
        put(OffboardingChecklistItem.Category.FINANCE, List.of(
                "Salary Settlement", "Reimbursements", "Loans", "Advances"));
        put(OffboardingChecklistItem.Category.MANAGER, List.of(
                "Knowledge Transfer", "Project Handover", "Documentation Completed"));
    }};

    // Called both from the dedicated /offboarding/initiate endpoint and (to keep the existing
    // simple contract working) from EmployeeService.recordResignation - creates the case once per
    // employee; a second resignation call on an already-active case just returns the existing one.
    @Transactional
    public OffboardingCaseResponse initiateOffboarding(UUID employeeId, InitiateOffboardingRequest request, UUID initiatedById) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        Optional<OffboardingCase> existingActive = caseRepository.findByEmployeeId(employeeId).stream()
                .filter(c -> c.getStatus() == OffboardingCase.CaseStatus.IN_PROGRESS)
                .findFirst();
        if (existingActive.isPresent()) {
            return toResponse(existingActive.get(), true);
        }

        OffboardingCase offboardingCase = OffboardingCase.builder()
                .employee(employee)
                .initiatedDate(LocalDate.now())
                .lastWorkingDate(request.lastWorkingDate())
                .reason(request.reason())
                .type(request.type() != null ? request.type() : OffboardingCase.Type.RESIGNATION)
                .stage(Stage.RESIGNATION_SUBMITTED)
                .status(OffboardingCase.CaseStatus.IN_PROGRESS)
                .bgvRequired(request.bgvRequired())
                .initiatedById(initiatedById)
                .build();
        offboardingCase = caseRepository.save(offboardingCase);

        employee.setLifecycleStatus(Employee.LifecycleStatus.OFFBOARDING);
        employeeRepository.save(employee);

        seedChecklist(offboardingCase);
        recordHistory(offboardingCase, null, Stage.RESIGNATION_SUBMITTED, initiatedById, "Offboarding initiated");

        auditService.record("Offboarding Initiated", employee.getEmployeeId(),
                offboardingCase.getType() + " - last working day " + offboardingCase.getLastWorkingDate());

        notifyStage(offboardingCase, "Offboarding initiated for " + employeeName(employee));

        return toResponse(offboardingCase, true);
    }

    private void seedChecklist(OffboardingCase offboardingCase) {
        List<OffboardingChecklistItem> items = new ArrayList<>();
        DEFAULT_CHECKLIST.forEach((category, labels) -> labels.forEach(label ->
                items.add(OffboardingChecklistItem.builder()
                        .offboardingCase(offboardingCase)
                        .category(category)
                        .label(label)
                        .status(OffboardingChecklistItem.Status.PENDING)
                        .build())));

        // One dynamic IT checklist item per asset actually assigned to this employee right now,
        // replacing the old generic "Laptop Returned"/"Charger Returned"/"ID Card Returned"
        // placeholders. Each item is linked back to its AssetAssignment (assetAssignmentId) so
        // completing it also returns the real asset - see updateChecklistItem().
        List<AssetAssignmentResponse> activeAssignments = assetService.getEmployeeAssets(offboardingCase.getEmployee().getId());
        for (AssetAssignmentResponse assignment : activeAssignments) {
            items.add(OffboardingChecklistItem.builder()
                    .offboardingCase(offboardingCase)
                    .category(OffboardingChecklistItem.Category.IT)
                    .label("Return " + assignment.getAssetName() + " (" + assignment.getAssetTag() + ")")
                    .status(OffboardingChecklistItem.Status.PENDING)
                    .assetAssignmentId(assignment.getId())
                    .build());
        }
        checklistRepository.saveAll(items);
    }

    @Transactional(readOnly = true)
    public OffboardingCaseResponse getCase(UUID caseId) {
        return toResponse(findCase(caseId), true);
    }

    @Transactional(readOnly = true)
    public OffboardingCaseResponse getCaseByEmployee(UUID employeeId) {
        OffboardingCase offboardingCase = caseRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("No offboarding case found for this employee"));
        return toResponse(offboardingCase, true);
    }

    @Transactional(readOnly = true)
    public boolean hasCase(UUID employeeId) {
        return caseRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employeeId).isPresent();
    }

    @Transactional
    public OffboardingCaseResponse advanceStage(UUID caseId, AdvanceStageRequest request, UUID actorId) {
        OffboardingCase offboardingCase = findCase(caseId);
        if (offboardingCase.getStatus() != OffboardingCase.CaseStatus.IN_PROGRESS) {
            throw new BadRequestException("This offboarding case is no longer in progress");
        }
        Stage from = offboardingCase.getStage();
        Stage to = request.stage() != null ? request.stage() : nextStage(offboardingCase);
        if (to == null) {
            throw new BadRequestException("This case is already at the final stage");
        }
        offboardingCase.setStage(to);
        if (to == Stage.COMPLETED) {
            offboardingCase.setStatus(OffboardingCase.CaseStatus.COMPLETED);
            Employee exitingEmployee = offboardingCase.getEmployee();
            exitingEmployee.setLifecycleStatus(Employee.LifecycleStatus.EXITED);
            employeeRepository.save(exitingEmployee);
        }
        offboardingCase = caseRepository.save(offboardingCase);

        recordHistory(offboardingCase, from, to, actorId, request.comments());
        auditService.record("Offboarding Stage Advanced", offboardingCase.getEmployee().getEmployeeId(),
                from + " -> " + to);
        notifyStage(offboardingCase, "Offboarding for " + employeeName(offboardingCase.getEmployee())
                + " moved to " + to.name().replace('_', ' '));

        return toResponse(offboardingCase, true);
    }

    // Cancels an in-progress case (e.g. resignation withdrawn) without deleting its history.
    @Transactional
    public OffboardingCaseResponse cancelCase(UUID caseId, UUID actorId, String comments) {
        OffboardingCase offboardingCase = findCase(caseId);
        offboardingCase.setStatus(OffboardingCase.CaseStatus.CANCELLED);
        offboardingCase = caseRepository.save(offboardingCase);
        recordHistory(offboardingCase, offboardingCase.getStage(), offboardingCase.getStage(), actorId,
                "Case cancelled" + (comments != null && !comments.isBlank() ? ": " + comments : ""));
        auditService.record("Offboarding Cancelled", offboardingCase.getEmployee().getEmployeeId(), comments);
        return toResponse(offboardingCase, true);
    }

    private Stage nextStage(OffboardingCase offboardingCase) {
        int idx = ORDER.indexOf(offboardingCase.getStage());
        for (int i = idx + 1; i < ORDER.size(); i++) {
            Stage candidate = ORDER.get(i);
            if (candidate == Stage.BGV_EXIT_VERIFICATION && !offboardingCase.isBgvRequired()) continue;
            return candidate;
        }
        return null;
    }

    @Transactional
    public ChecklistItemResponse updateChecklistItem(UUID itemId, ChecklistUpdateRequest request, UUID actorId) {
        OffboardingChecklistItem item = checklistRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Checklist item not found"));
        if (request.status() != null) {
            item.setStatus(request.status());
            if (request.status() == OffboardingChecklistItem.Status.COMPLETED) {
                item.setCompletedById(actorId);
                item.setCompletedAt(LocalDateTime.now());
                // If this item represents a real assigned asset, completing it in the offboarding
                // checklist also returns the asset itself (assignment closed, asset -> AVAILABLE),
                // keeping Asset Management in sync instead of just flipping a checklist flag.
                if (item.getAssetAssignmentId() != null) {
                    assetService.markAssignmentReturned(item.getAssetAssignmentId(), actorId);
                }
            } else {
                item.setCompletedById(null);
                item.setCompletedAt(null);
            }
        }
        if (request.remarks() != null) item.setRemarks(request.remarks());
        item = checklistRepository.save(item);

        auditService.record("Offboarding Checklist Updated", item.getOffboardingCase().getEmployee().getEmployeeId(),
                item.getCategory() + " - " + item.getLabel() + " -> " + item.getStatus());

        return toChecklistResponse(item);
    }

    @Transactional(readOnly = true)
    public List<OffboardingCaseSummary> listCases(OffboardingCase.CaseStatus status, Stage stage, UUID departmentId, UUID managerIdFilter) {
        List<OffboardingCase> cases = caseRepository.findAll();
        return cases.stream()
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> stage == null || c.getStage() == stage)
                .filter(c -> departmentId == null || (c.getEmployee().getDepartment() != null
                        && departmentId.equals(c.getEmployee().getDepartment().getId())))
                .filter(c -> managerIdFilter == null || managerIdFilter.equals(c.getEmployee().getReportingManagerId()))
                .sorted(Comparator.comparing(OffboardingCase::getCreatedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public OffboardingDashboardStats getDashboardStats() {
        List<OffboardingCase> all = caseRepository.findAll();
        long active = all.stream().filter(c -> c.getStatus() == OffboardingCase.CaseStatus.IN_PROGRESS).count();
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        long completedThisMonth = all.stream()
                .filter(c -> c.getStatus() == OffboardingCase.CaseStatus.COMPLETED)
                .filter(c -> c.getUpdatedAt() != null && !c.getUpdatedAt().toLocalDate().isBefore(monthStart))
                .count();
        Map<String, Long> byStage = new LinkedHashMap<>();
        for (Stage s : Stage.values()) {
            long count = all.stream().filter(c -> c.getStatus() == OffboardingCase.CaseStatus.IN_PROGRESS && c.getStage() == s).count();
            if (count > 0) byStage.put(s.name(), count);
        }
        return new OffboardingDashboardStats(active, completedThisMonth, byStage);
    }

    // Eligible for exit-package generation once HR has given final approval (or later stages).
    private static final Set<Stage> EXIT_PACKAGE_ELIGIBLE_STAGES = Set.of(
            Stage.FINAL_HR_APPROVAL, Stage.EXIT_DOCS_GENERATED, Stage.COMPLETED);

    @Transactional(readOnly = true)
    public byte[] buildExitPackageZip(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        List<Document> documents = collectExitDocuments(employeeId);
        if (documents.isEmpty()) {
            throw new BadRequestException("No generated documents are available yet for this employee's exit package");
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<String> usedNames = new HashSet<>();
            for (Document doc : documents) {
                byte[] bytes = fetchBytes(doc.getFileUrl());
                if (bytes == null) continue;
                String name = uniqueName(usedNames, safeFileName(doc));
                zos.putNextEntry(new ZipEntry(name));
                zos.write(bytes);
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Could not build exit package zip for " + employee.getEmployeeId(), e);
        }
    }

    @Transactional
    public void emailExitPackage(UUID employeeId, UUID actorId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        String toEmail = employee.getPersonalEmail() != null && !employee.getPersonalEmail().isBlank()
                ? employee.getPersonalEmail() : employee.getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            throw new BadRequestException("Employee has no email address to send the exit package to");
        }
        List<Document> documents = collectExitDocuments(employeeId);
        List<EmailService.Attachment> attachments = new ArrayList<>();
        for (Document doc : documents) {
            byte[] bytes = fetchBytes(doc.getFileUrl());
            if (bytes == null) continue;
            attachments.add(new EmailService.Attachment(safeFileName(doc), bytes));
        }
        emailService.sendExitPackageEmail(toEmail, employeeName(employee), attachments);
        auditService.record("Exit Package Emailed", employee.getEmployeeId(),
                attachments.size() + " document(s) sent to " + toEmail);

        caseRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employeeId).ifPresent(c ->
                recordHistory(c, c.getStage(), c.getStage(), actorId, "Exit package emailed to " + toEmail));
    }

    // Only documents that genuinely exist for this employee are ever bundled - no fabricated
    // placeholders. Covers every exit-relevant DocumentType that DocumentGenerationService can
    // actually produce for this system (experience/relieving letters, offer/appointment letters,
    // any hike/promotion letters, and payslips).
    private List<Document> collectExitDocuments(UUID employeeId) {
        List<Document> all = documentRepository.findByEmployeeId(employeeId);
        Set<Document.DocumentType> exitTypes = Set.of(
                Document.DocumentType.EXPERIENCE_LETTER, Document.DocumentType.RELIEVING_LETTER,
                Document.DocumentType.OFFER_LETTER, Document.DocumentType.APPOINTMENT_LETTER,
                Document.DocumentType.PROMOTION_LETTER, Document.DocumentType.SALARY_REVISION_LETTER,
                Document.DocumentType.SALARY_CERTIFICATE, Document.DocumentType.PAYSLIP,
                Document.DocumentType.RESIGNATION_ACCEPTANCE_LETTER);
        return all.stream()
                .filter(d -> d.isActive() && exitTypes.contains(d.getType()) && d.getFileUrl() != null)
                .toList();
    }

    private byte[] fetchBytes(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 300) {
                log.warn("Could not fetch document from {} (status {})", url, resp.statusCode());
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            log.warn("Failed to fetch document from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String safeFileName(Document doc) {
        String base = (doc.getFileName() != null && !doc.getFileName().isBlank())
                ? doc.getFileName() : doc.getTitle();
        String cleaned = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (!cleaned.contains(".")) cleaned = cleaned + ".pdf";
        return cleaned;
    }

    private String uniqueName(Set<String> used, String name) {
        String candidate = name;
        int i = 1;
        while (!used.add(candidate)) {
            String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
            candidate = base + "_" + (++i) + ext;
        }
        return candidate;
    }

    private OffboardingCase findCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new EntityNotFoundException("Offboarding case not found"));
    }

    private void recordHistory(OffboardingCase offboardingCase, Stage from, Stage to, UUID actorId, String comments) {
        historyRepository.save(OffboardingStageHistory.builder()
                .offboardingCase(offboardingCase)
                .fromStage(from)
                .toStage(to)
                .changedById(actorId)
                .changedAt(LocalDateTime.now())
                .comments(comments)
                .build());
    }

    // Notifies HR/CEO/HR_MANAGER users plus the employee's reporting manager on every stage
    // transition - mirrors the notification call pattern already used by RecruitmentService/
    // OnboardingService (NotificationService.sendNotification(...)).
    private void notifyStage(OffboardingCase offboardingCase, String message) {
        UUID managerId = offboardingCase.getEmployee().getReportingManagerId();
        if (managerId != null) {
            employeeRepository.findById(managerId).ifPresent(manager -> {
                if (manager.getUser() != null) {
                    notificationService.sendNotification(manager.getUser().getId(), "Offboarding Update", message,
                            NotificationType.OFFBOARDING, offboardingCase.getId(), "OFFBOARDING_CASE");
                }
            });
        }
        for (Employee hr : employeeRepository.findAllManagers()) {
            if (hr.getUser() != null && (hr.getUser().getRole().name().equals("HR_MANAGER")
                    || hr.getUser().getRole().name().equals("CEO"))) {
                notificationService.sendNotification(hr.getUser().getId(), "Offboarding Update", message,
                        NotificationType.OFFBOARDING, offboardingCase.getId(), "OFFBOARDING_CASE");
            }
        }
    }

    private String employeeName(Employee e) {
        return e.getFirstName() + " " + e.getLastName();
    }

    private OffboardingCaseResponse toResponse(OffboardingCase c, boolean includeDetails) {
        Employee e = c.getEmployee();
        int daysInStage = daysInStage(c);
        List<ChecklistItemResponse> checklist = includeDetails
                ? checklistRepository.findByOffboardingCaseIdOrderByCategoryAsc(c.getId()).stream().map(this::toChecklistResponse).toList()
                : List.of();
        List<StageHistoryResponse> history = includeDetails
                ? historyRepository.findByOffboardingCaseIdOrderByChangedAtAsc(c.getId()).stream().map(this::toHistoryResponse).toList()
                : List.of();
        // Computed (not persisted) rather than auto-advancing the pipeline: advanceStage() today is
        // purely manual/HR-driven for every stage, with no existing precedent for a stage
        // auto-completing itself. Auto-advancing just IT_CLEARANCE would be an inconsistent special
        // case, so instead we surface eligibility and let HR click the existing advance button once
        // every IT item (dynamic per-asset returns + access/account items) is COMPLETED.
        List<ChecklistItemResponse> itItems = checklist.stream()
                .filter(i -> i.category() == OffboardingChecklistItem.Category.IT).toList();
        boolean itClearanceEligible = !itItems.isEmpty()
                && itItems.stream().allMatch(i -> i.status() == OffboardingChecklistItem.Status.COMPLETED);
        return new OffboardingCaseResponse(
                c.getId(), e.getId(), e.getEmployeeId(), employeeName(e),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getReportingManagerId(), c.getInitiatedDate(), c.getLastWorkingDate(), c.getReason(),
                c.getType(), c.getStage(), c.getStatus(), c.isBgvRequired(), daysInStage, itClearanceEligible,
                checklist, history);
    }

    private OffboardingCaseSummary toSummary(OffboardingCase c) {
        Employee e = c.getEmployee();
        List<OffboardingChecklistItem> items = checklistRepository.findByOffboardingCaseIdOrderByCategoryAsc(c.getId());
        long completed = items.stream().filter(i -> i.getStatus() == OffboardingChecklistItem.Status.COMPLETED).count();
        return new OffboardingCaseSummary(
                c.getId(), e.getId(), e.getEmployeeId(), employeeName(e),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                c.getType(), c.getStage(), c.getStatus(), c.getLastWorkingDate(), daysInStage(c),
                (int) completed, items.size());
    }

    private int daysInStage(OffboardingCase c) {
        LocalDateTime since = historyRepository.findByOffboardingCaseIdOrderByChangedAtAsc(c.getId()).stream()
                .filter(h -> h.getToStage() == c.getStage())
                .reduce((first, second) -> second)
                .map(OffboardingStageHistory::getChangedAt)
                .orElse(c.getCreatedAt());
        return (int) ChronoUnit.DAYS.between(since.toLocalDate(), LocalDate.now());
    }

    private ChecklistItemResponse toChecklistResponse(OffboardingChecklistItem item) {
        String completedByName = item.getCompletedById() != null
                ? employeeRepository.findByUserId(item.getCompletedById())
                        .or(() -> employeeRepository.findById(item.getCompletedById()))
                        .map(this::employeeName).orElse(null)
                : null;
        return new ChecklistItemResponse(item.getId(), item.getCategory(), item.getLabel(), item.getStatus(),
                item.getCompletedById(), completedByName, item.getCompletedAt(), item.getRemarks());
    }

    private StageHistoryResponse toHistoryResponse(OffboardingStageHistory h) {
        String changedByName = h.getChangedById() != null
                ? employeeRepository.findByUserId(h.getChangedById())
                        .or(() -> employeeRepository.findById(h.getChangedById()))
                        .map(this::employeeName).orElse(null)
                : null;
        return new StageHistoryResponse(h.getId(), h.getFromStage(), h.getToStage(), h.getChangedById(),
                changedByName, h.getChangedAt(), h.getComments());
    }
}
