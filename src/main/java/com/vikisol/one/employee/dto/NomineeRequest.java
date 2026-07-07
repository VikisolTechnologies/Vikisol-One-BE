package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.Employee;

import java.time.LocalDate;

public record NomineeRequest(
        String name,
        String relation,
        LocalDate dateOfBirth,
        Integer sharePercentage,
        Employee.Gender gender
) {
}
