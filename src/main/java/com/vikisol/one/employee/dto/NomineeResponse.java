package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.Employee;

import java.time.LocalDate;
import java.util.UUID;

public record NomineeResponse(
        UUID id,
        String name,
        String relation,
        LocalDate dateOfBirth,
        Integer sharePercentage,
        Employee.Gender gender
) {
}
