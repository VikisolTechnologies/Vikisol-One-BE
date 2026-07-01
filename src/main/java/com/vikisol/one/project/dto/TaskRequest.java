package com.vikisol.one.project.dto;

import com.vikisol.one.project.entity.Task;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class TaskRequest {
    @NotBlank private String title;
    private String description;
    private UUID assigneeId;
    private String assigneeName;
    private Task.Status status;
    private Task.Priority priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private double estimatedHours;
    private UUID parentTaskId;
}
