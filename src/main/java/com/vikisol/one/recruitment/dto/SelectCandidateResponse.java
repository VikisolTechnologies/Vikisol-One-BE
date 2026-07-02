package com.vikisol.one.recruitment.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record SelectCandidateResponse(
        UUID candidateId,
        String employeeId,
        String candidateName,
        String candidateEmail,
        Map<String, BigDecimal> ctcBreakup,
        boolean emailSent
) {
}
