package com.vikisol.one.employee.dto;

import java.time.LocalDate;

public record ResignationRequest(
        LocalDate lastWorkingDate,
        String reason
) {
}
