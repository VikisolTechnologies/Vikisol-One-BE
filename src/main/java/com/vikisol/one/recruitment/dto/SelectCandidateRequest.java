package com.vikisol.one.recruitment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SelectCandidateRequest(
        UUID designationId,
        UUID departmentId,
        BigDecimal offeredCtc,
        LocalDate dateOfJoining
) {
}
