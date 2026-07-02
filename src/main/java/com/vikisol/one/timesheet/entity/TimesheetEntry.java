package com.vikisol.one.timesheet.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.project.entity.Project;
import com.vikisol.one.project.entity.Task;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "timesheet_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "project_id", "date"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = true)
public class TimesheetEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Double hours;

    private String description;

    private LocalTime checkInTime;

    private LocalTime checkOutTime;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private WorkLocation workLocation = WorkLocation.OFFICE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    private UUID approvedById;

    public enum Status { DRAFT, SUBMITTED, APPROVED, REJECTED }

    public enum WorkLocation { OFFICE, REMOTE, CLIENT_LOCATION }
}
