package com.vikisol.one.project.service;

import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.project.dto.*;
import com.vikisol.one.project.entity.Project;
import com.vikisol.one.project.entity.ProjectMember;
import com.vikisol.one.project.entity.Task;
import com.vikisol.one.project.repository.ProjectMemberRepository;
import com.vikisol.one.project.repository.ProjectRepository;
import com.vikisol.one.project.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

    // ─── Projects ───

    public ProjectResponse createProject(ProjectRequest request) {
        Project project = Project.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .clientName(request.getClientName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(request.getStatus() != null ? request.getStatus() : Project.Status.NOT_STARTED)
                .priority(request.getPriority() != null ? request.getPriority() : Project.Priority.MEDIUM)
                .projectManagerId(request.getProjectManagerId())
                .budget(request.getBudget())
                .build();
        return mapProject(projectRepository.save(project));
    }

    public ProjectResponse updateProject(UUID id, ProjectRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        project.setName(request.getName());
        project.setCode(request.getCode());
        project.setDescription(request.getDescription());
        project.setClientName(request.getClientName());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        if (request.getStatus() != null) project.setStatus(request.getStatus());
        if (request.getPriority() != null) project.setPriority(request.getPriority());
        project.setProjectManagerId(request.getProjectManagerId());
        project.setBudget(request.getBudget());
        return mapProject(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID id) {
        return mapProject(projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project not found")));
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getActiveProjects(Pageable pageable) {
        return projectRepository.findByIsActiveTrue(pageable).map(this::mapProject);
    }

    public void deleteProject(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        project.setActive(false);
        projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getMyProjects(UUID employeeId) {
        return projectMemberRepository.findByEmployeeIdAndIsActiveTrue(employeeId)
                .stream().map(pm -> mapProject(pm.getProject())).toList();
    }

    // ─── Members ───

    public ProjectMemberResponse addMember(UUID projectId, ProjectMemberRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        ProjectMember member = ProjectMember.builder()
                .project(project)
                .employee(employee)
                .role(request.getRole())
                .allocationPercentage(request.getAllocationPercentage())
                .startDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now())
                .endDate(request.getEndDate())
                .build();
        return mapMember(projectMemberRepository.save(member));
    }

    public void removeMember(UUID memberId) {
        ProjectMember member = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Project member not found"));
        member.setActive(false);
        projectMemberRepository.save(member);
    }

    // Active-only, matching mapProject()'s teamSize (countByProjectIdAndIsActiveTrue) - previously
    // this returned every member ever added including ones removed via removeMember() (which only
    // soft-deletes via isActive=false), so the detail view listed more people than the card's
    // count, e.g. a project card showing "2 members" but the detail modal listing 3.
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(UUID projectId) {
        return projectMemberRepository.findByProjectIdAndIsActiveTrue(projectId)
                .stream().map(this::mapMember).toList();
    }

    // ─── Tasks ───

    public TaskResponse createTask(UUID projectId, TaskRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        Task task = Task.builder()
                .project(project)
                .title(request.getTitle())
                .description(request.getDescription())
                .assigneeId(request.getAssigneeId())
                .assigneeName(request.getAssigneeName())
                .status(request.getStatus() != null ? request.getStatus() : Task.Status.TODO)
                .priority(request.getPriority() != null ? request.getPriority() : Task.Priority.MEDIUM)
                .startDate(request.getStartDate())
                .dueDate(request.getDueDate())
                .estimatedHours(request.getEstimatedHours())
                .parentTaskId(request.getParentTaskId())
                .build();
        return mapTask(taskRepository.save(task));
    }

    public TaskResponse updateTask(UUID taskId, TaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setAssigneeId(request.getAssigneeId());
        task.setAssigneeName(request.getAssigneeName());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        task.setStartDate(request.getStartDate());
        task.setDueDate(request.getDueDate());
        task.setEstimatedHours(request.getEstimatedHours());
        task.setParentTaskId(request.getParentTaskId());
        if (request.getStatus() == Task.Status.DONE) {
            task.setCompletedDate(LocalDate.now());
        }
        return mapTask(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId) {
        return mapTask(taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found")));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByProject(UUID projectId) {
        return taskRepository.findByProjectId(projectId).stream().map(this::mapTask).toList();
    }

    public void deleteTask(UUID taskId) {
        taskRepository.deleteById(taskId);
    }

    // ─── Dashboard ───

    @Transactional(readOnly = true)
    public Map<String, Object> getProjectDashboard(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("project", mapProject(project));
        dashboard.put("totalMembers", members.size());
        dashboard.put("totalTasks", tasks.size());
        dashboard.put("completedTasks", tasks.stream().filter(t -> t.getStatus() == Task.Status.DONE).count());
        dashboard.put("inProgressTasks", tasks.stream().filter(t -> t.getStatus() == Task.Status.IN_PROGRESS).count());
        dashboard.put("blockedTasks", tasks.stream().filter(t -> t.getStatus() == Task.Status.BLOCKED).count());
        dashboard.put("totalEstimatedHours", tasks.stream().mapToDouble(Task::getEstimatedHours).sum());
        dashboard.put("totalActualHours", tasks.stream().mapToDouble(Task::getActualHours).sum());
        return dashboard;
    }

    // ─── Mappers ───

    // Project budget is confidential to the CEO only (Super Admin/HR/Manager/Team Lead/Employee
    // must never receive it, even though they can otherwise read this same project). Since every
    // read path (get/list/my-projects/dashboard) shares this one mapper, redacting here is the
    // single point of enforcement - callers cannot bypass it by hitting a different endpoint.
    private boolean callerIsCeo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_CEO"::equals);
    }

    private ProjectResponse mapProject(Project p) {
        ProjectResponse r = new ProjectResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setCode(p.getCode());
        r.setDescription(p.getDescription());
        r.setClientName(p.getClientName());
        r.setStartDate(p.getStartDate());
        r.setEndDate(p.getEndDate());
        r.setStatus(p.getStatus());
        r.setPriority(p.getPriority());
        r.setProjectManagerId(p.getProjectManagerId());
        if (p.getProjectManagerId() != null) {
            employeeRepository.findById(p.getProjectManagerId())
                    .ifPresent(mgr -> r.setProjectManagerName(mgr.getFirstName() + " " + mgr.getLastName()));
        }
        r.setTeamSize((int) projectMemberRepository.countByProjectIdAndIsActiveTrue(p.getId()));
        r.setBudget(callerIsCeo() ? p.getBudget() : null);
        r.setActive(p.isActive());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }

    private ProjectMemberResponse mapMember(ProjectMember m) {
        ProjectMemberResponse r = new ProjectMemberResponse();
        r.setId(m.getId());
        r.setProjectId(m.getProject().getId());
        r.setProjectName(m.getProject().getName());
        r.setEmployeeId(m.getEmployee().getId());
        r.setEmployeeName(m.getEmployee().getFirstName() + " " + m.getEmployee().getLastName());
        r.setRole(m.getRole());
        r.setAllocationPercentage(m.getAllocationPercentage());
        r.setStartDate(m.getStartDate());
        r.setEndDate(m.getEndDate());
        r.setActive(m.isActive());
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());
        return r;
    }

    private TaskResponse mapTask(Task t) {
        TaskResponse r = new TaskResponse();
        r.setId(t.getId());
        r.setProjectId(t.getProject().getId());
        r.setProjectName(t.getProject().getName());
        r.setTitle(t.getTitle());
        r.setDescription(t.getDescription());
        r.setAssigneeId(t.getAssigneeId());
        r.setAssigneeName(t.getAssigneeName());
        r.setStatus(t.getStatus());
        r.setPriority(t.getPriority());
        r.setStartDate(t.getStartDate());
        r.setDueDate(t.getDueDate());
        r.setCompletedDate(t.getCompletedDate());
        r.setEstimatedHours(t.getEstimatedHours());
        r.setActualHours(t.getActualHours());
        r.setParentTaskId(t.getParentTaskId());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        return r;
    }
}
