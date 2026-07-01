package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.Employee;

import java.util.UUID;

public record EmployeeListResponse(
        UUID id,
        String visibleId,
        String firstName,
        String lastName,
        String email,
        String departmentName,
        String designationTitle,
        Employee.EmploymentStatus employmentStatus,
        String profilePictureUrl
) {
}
