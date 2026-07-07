package com.vikisol.one.project.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.project.dto.*;
import com.vikisol.one.project.service.ProjectService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final com.vikisol.one.employee.repository.EmployeeRepository employeeRepository;

    // ─── Projects ───

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getProjects(Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Projects retrieved",
                projectService.getActiveProjects(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Project retrieved",
                projectService.getProject(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Project created",
                projectService.createProject(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Project updated",
                projectService.updateProject(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Project deleted", null));
    }

    // ProjectMember.employee is keyed by the Employee table's PK, not the User's - principal.getId()
    // is the User id, a different UUID for any real account. Resolving through EmployeeRepository
    // here is what makes "my projects" actually match the ProjectMember rows CEO/HR created.
    @GetMapping("/my-projects")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getMyProjects(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID employeeId = employeeRepository.findByUserId(principal.getId())
                .map(com.vikisol.one.employee.entity.Employee::getId)
                .orElseThrow(() -> new com.vikisol.one.common.exception.BadRequestException("No employee record is linked to this account"));
        return ResponseEntity.ok(new ApiResponse<>(true, "My projects retrieved",
                projectService.getMyProjects(employeeId)));
    }

    // ─── Members ───

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> addMember(
            @PathVariable UUID projectId, @Valid @RequestBody ProjectMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Member added",
                projectService.addMember(projectId, request)));
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID projectId, @PathVariable UUID memberId) {
        projectService.removeMember(memberId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Member removed", null));
    }

    @GetMapping("/{projectId}/members")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> getMembers(@PathVariable UUID projectId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Members retrieved",
                projectService.getMembers(projectId)));
    }

    // ─── Tasks ───

    @PostMapping("/{projectId}/tasks")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @PathVariable UUID projectId, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Task created",
                projectService.createTask(projectId, request)));
    }

    @PutMapping("/{projectId}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable UUID projectId, @PathVariable UUID taskId,
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Task updated",
                projectService.updateTask(taskId, request)));
    }

    @GetMapping("/{projectId}/tasks")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(@PathVariable UUID projectId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Tasks retrieved",
                projectService.getTasksByProject(projectId)));
    }

    @GetMapping("/{projectId}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            @PathVariable UUID projectId, @PathVariable UUID taskId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Task retrieved",
                projectService.getTask(taskId)));
    }

    @DeleteMapping("/{projectId}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable UUID projectId, @PathVariable UUID taskId) {
        projectService.deleteTask(taskId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Task deleted", null));
    }

    // ─── Dashboard ───

    @GetMapping("/{projectId}/dashboard")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProjectDashboard(@PathVariable UUID projectId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Dashboard retrieved",
                projectService.getProjectDashboard(projectId)));
    }
}
