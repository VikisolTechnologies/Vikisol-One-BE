package com.vikisol.one.timesheet.service;

import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.common.exception.ResourceNotFoundException;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.project.entity.Project;
import com.vikisol.one.project.entity.Task;
import com.vikisol.one.project.repository.ProjectRepository;
import com.vikisol.one.project.repository.TaskRepository;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.timesheet.dto.*;
import com.vikisol.one.timesheet.entity.TimesheetEntry;
import com.vikisol.one.timesheet.repository.TimesheetEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TimesheetService {

    private final TimesheetEntryRepository entryRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public TimesheetEntryResponse createEntry(TimesheetEntryRequest request, UserPrincipal principal) {
        Employee emp = getEmployee(principal);
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        Task task = request.taskId() != null
                ? taskRepository.findById(request.taskId()).orElse(null) : null;

        TimesheetEntry entry = new TimesheetEntry();
        entry.setEmployee(emp);
        entry.setProject(project);
        entry.setTask(task);
        entry.setDate(request.date());
        entry.setHours(request.hours());
        entry.setDescription(request.description());
        entry.setStatus(TimesheetEntry.Status.DRAFT);
        return mapResponse(entryRepository.save(entry));
    }

    public TimesheetEntryResponse updateEntry(UUID id, TimesheetEntryRequest request, UserPrincipal principal) {
        TimesheetEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found"));
        if (entry.getStatus() != TimesheetEntry.Status.DRAFT && entry.getStatus() != TimesheetEntry.Status.REJECTED) {
            throw new BadRequestException("Cannot edit submitted/approved entries");
        }
        entry.setHours(request.hours());
        entry.setDescription(request.description());
        entry.setDate(request.date());
        return mapResponse(entryRepository.save(entry));
    }

    public void submitEntries(TimesheetSubmitRequest request, UserPrincipal principal) {
        for (UUID id : request.entryIds()) {
            TimesheetEntry entry = entryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Entry not found: " + id));
            entry.setStatus(TimesheetEntry.Status.SUBMITTED);
            entryRepository.save(entry);
        }
    }

    public TimesheetEntryResponse processAction(UUID id, String action, UserPrincipal principal) {
        TimesheetEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found"));
        Employee manager = getEmployee(principal);
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
        List<Employee> reports = employeeRepository.findByReportingManagerId(manager.getId());
        return reports.stream()
                .flatMap(e -> entryRepository.findByEmployeeIdAndDateBetween(e.getId(), start, end).stream())
                .map(this::mapResponse).toList();
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
        return new TimesheetEntryResponse(
                e.getId(), e.getEmployee().getId(),
                e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName(),
                e.getProject().getId(), e.getProject().getName(),
                e.getTask() != null ? e.getTask().getId() : null,
                e.getTask() != null ? e.getTask().getTitle() : null,
                e.getDate(), e.getHours(), e.getDescription(),
                e.getStatus().name(), e.getApprovedById()
        );
    }
}
