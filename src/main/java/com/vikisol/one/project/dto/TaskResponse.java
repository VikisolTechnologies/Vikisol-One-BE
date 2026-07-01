package com.vikisol.one.project.dto;

import com.vikisol.one.project.entity.Task;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TaskResponse {
    private UUID id;
    private UUID projectId;
    private String projectName;
    private String title;
    private String description;
    private UUID assigneeId;
    private String assigneeName;
    private Task.Status status;
    private Task.Priority priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDate completedDate;
    private double estimatedHours;
    private double actualHours;
    private UUID parentTaskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
