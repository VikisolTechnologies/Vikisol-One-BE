package com.vikisol.one.timesheet.service;

import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.common.exception.ResourceNotFoundException;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.notification.entity.Notification;
import com.vikisol.one.notification.service.NotificationService;
import com.vikisol.one.project.entity.Project;
import com.vikisol.one.project.entity.Task;
import com.vikisol.one.project.repository.ProjectRepository;
import com.vikisol.one.project.repository.TaskRepository;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.timesheet.dto.*;
import com.vikisol.one.timesheet.entity.TimesheetEntry;
import com.vikisol.one.timesheet.repository.TimesheetEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TimesheetService {

    private final TimesheetEntryRepository entryRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    private Project resolveProject(UUID projectId) {
        if (projectId == null) return null; // Bench - non-billable, no project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.isActive()) {
            throw new BadRequestException("Cannot log time against an inactive project");
        }
        return project;
    }

    public TimesheetEntryResponse createEntry(TimesheetEntryRequest request, UserPrincipal principal) {
        Employee emp = getEmployee(principal);
        Project project = resolveProject(request.projectId());
        Task task = request.taskId() != null
                ? taskRepository.findById(request.taskId()).orElse(null) : null;

        TimesheetEntry entry = new TimesheetEntry();
        entry.setEmployee(emp);
        entry.setProject(project);
        entry.setTask(task);
        entry.setDate(request.date());
        entry.setHours(resolveHours(request));
        entry.setDescription(request.description());
        entry.setCheckInTime(request.checkInTime());
        entry.setCheckOutTime(request.checkOutTime());
        entry.setReason(request.reason());
        if (request.workLocation() != null) entry.setWorkLocation(request.workLocation());
        entry.setStatus(TimesheetEntry.Status.DRAFT);
        return mapResponse(entryRepository.save(entry));
    }

    /** Prefers computing hours worked from punch in/out; falls back to a manually entered value. */
    private Double resolveHours(TimesheetEntryRequest request) {
        if (request.checkInTime() != null && request.checkOutTime() != null) {
            double hrs = java.time.Duration.between(request.checkInTime(), request.checkOutTime()).toMinutes() / 60.0;
            if (hrs < 0) throw new BadRequestException("Punch out time must be after punch in time");
            return Math.round(hrs * 100.0) / 100.0;
        }
        if (request.hours() == null) {
            throw new BadRequestException("Provide either punch in/out times or hours worked");
        }
        return request.hours();
    }

    public TimesheetEntryResponse updateEntry(UUID id, TimesheetEntryRequest request, UserPrincipal principal) {
        TimesheetEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found"));
        if (entry.getStatus() != TimesheetEntry.Status.DRAFT && entry.getStatus() != TimesheetEntry.Status.REJECTED) {
            throw new BadRequestException("Cannot edit submitted/approved entries");
        }
        entry.setProject(resolveProject(request.projectId()));
        entry.setHours(resolveHours(request));
        entry.setDescription(request.description());
        entry.setDate(request.date());
        entry.setCheckInTime(request.checkInTime());
        entry.setCheckOutTime(request.checkOutTime());
        entry.setReason(request.reason());
        if (request.workLocation() != null) entry.setWorkLocation(request.workLocation());
        return mapResponse(entryRepository.save(entry));
    }

    public void submitEntries(TimesheetSubmitRequest request, UserPrincipal principal) {
        LocalDate latestDate = null;
        for (UUID id : request.entryIds()) {
            TimesheetEntry entry = entryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Entry not found: " + id));
            entry.setStatus(TimesheetEntry.Status.SUBMITTED);
            entryRepository.save(entry);
            if (latestDate == null || entry.getDate().isAfter(latestDate)) latestDate = entry.getDate();
        }
        if (!request.entryIds().isEmpty()) {
            notifyManagerOfSubmission(getEmployee(principal), latestDate);
        }
    }

    private void notifyManagerOfSubmission(Employee employee, LocalDate weekDate) {
        if (employee.getReportingManagerId() == null) return;
        employeeRepository.findById(employee.getReportingManagerId()).ifPresent(manager -> {
            String employeeName = employee.getFirstName() + " " + employee.getLastName();
            String weekLabel = weekDate != null ? "week of " + weekDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "this week";
            if (manager.getUser() != null) {
                notificationService.sendNotification(
                        manager.getUser().getId(),
                        "Timesheet Submitted",
                        employeeName + " submitted their timesheet for " + weekLabel + " and it needs your approval.",
                        Notification.NotificationType.TIMESHEET,
                        employee.getId(),
                        "TIMESHEET"
                );
            }
            if (manager.getEmail() != null) {
                try {
                    emailService.sendTimesheetSubmittedNotification(manager.getEmail(), employeeName, weekLabel);
                } catch (Exception e) {
                    log.warn("Failed to email manager about timesheet submission: {}", e.getMessage());
                }
            }
        });
    }

    public TimesheetEntryResponse processAction(UUID id, String action, UserPrincipal principal) {
        TimesheetEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found"));
        Employee manager = getEmployee(principal);
        // Role check alone let any MANAGER approve/reject any employee's timesheet entry
        // company-wide - only that employee's actual reporting manager (or HR_MANAGER/CEO/ADMIN,
        // who oversee everyone) may act on it.
        boolean isCompanyWide = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!isCompanyWide && !manager.getId().equals(entry.getEmployee().getReportingManagerId())) {
            throw new BadRequestException("You can only act on timesheet entries from your own direct reports");
        }
        if ("APPROVE".equalsIgnoreCase(action)) {
            entry.setStatus(TimesheetEntry.Status.APPROVED);
            entry.setApprovedById(manager.getId());
        } else if ("REJECT".equalsIgnoreCase(action)) {
            entry.setStatus(TimesheetEntry.Status.REJECTED);
        }
        return mapResponse(entryRepository.save(entry));
    }

    public List<TimesheetEntryResponse> getMyEntries(UserPrincipal principal, LocalDate start, LocalDate end) {
        Employee emp = getEmployee(principal);
        return entryRepository.findByEmployeeIdAndDateBetween(emp.getId(), start, end)
                .stream().map(this::mapResponse).toList();
    }

    public List<TimesheetEntryResponse> getTeamEntries(UserPrincipal principal, LocalDate start, LocalDate end) {
        Employee manager = getEmployee(principal);
        // CEO/HR Manager/Admin oversee the whole company, not just their own direct reports -
        // without this they'd see an empty/near-empty "pending approvals" list since they
        // typically have no one reporting directly to them in the org chart.
        boolean isCompanyWide = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        List<UUID> employeeIds = isCompanyWide
                ? employeeRepository.findAll().stream().map(Employee::getId).toList()
                : employeeRepository.findByReportingManagerId(manager.getId()).stream().map(Employee::getId).toList();
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return entryRepository.findByEmployeeIdInAndDateBetween(employeeIds, start, end)
                .stream().map(this::mapResponse).toList();
    }

    public List<TimesheetEntryResponse> getProjectEntries(UUID projectId, LocalDate start, LocalDate end) {
        return entryRepository.findByProjectIdAndDateBetween(projectId, start, end)
                .stream().map(this::mapResponse).toList();
    }

    private Employee getEmployee(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private TimesheetEntryResponse mapResponse(TimesheetEntry e) {
        boolean billable = e.getProject() != null;
        return new TimesheetEntryResponse(
                e.getId(), e.getEmployee().getId(),
                e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName(),
                billable ? e.getProject().getId() : null,
                billable ? e.getProject().getName() : "Bench",
                e.getTask() != null ? e.getTask().getId() : null,
                e.getTask() != null ? e.getTask().getTitle() : null,
                e.getDate(), e.getHours(), e.getDescription(),
                e.getCheckInTime(), e.getCheckOutTime(), e.getReason(),
                e.getWorkLocation() != null ? e.getWorkLocation().name() : null,
                billable,
                e.getStatus().name(), e.getApprovedById()
        );
    }
}
