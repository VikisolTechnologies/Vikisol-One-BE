package com.vikisol.one.performance.dto;

import com.vikisol.one.performance.entity.ReviewCycle;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ReviewCycleResponse {

    private UUID id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private ReviewCycle.Status status;
    private ReviewCycle.Type type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
