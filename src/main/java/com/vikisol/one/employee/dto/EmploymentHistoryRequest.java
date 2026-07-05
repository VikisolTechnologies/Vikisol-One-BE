package com.vikisol.one.employee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmploymentHistoryRequest(
        String companyName,
        String designation,
        LocalDate joiningDate,
        LocalDate relievingDate,
        String skillsUsed,
        String managerName,
        String reasonForLeaving,
        String location,
        BigDecimal lastSalary,
        String offerLetterUrl,
        String experienceLetterUrl,
        String relievingLetterUrl
) {
}
