package com.vikisol.one.employee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HikeRequest(
        BigDecimal newAnnualCtc,
        LocalDate effectiveDate,
        String reason
) {
}
