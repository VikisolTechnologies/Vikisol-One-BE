package com.vikisol.one.performance.dto;

import com.vikisol.one.performance.entity.ReviewCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReviewCycleRequest {

    @NotBlank
    private String name;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private ReviewCycle.Type type;

    private ReviewCycle.Status status;
}
