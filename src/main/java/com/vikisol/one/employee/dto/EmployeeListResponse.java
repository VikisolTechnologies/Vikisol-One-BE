package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.Employee;

import java.time.LocalDate;
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
        String profilePictureUrl,
        // Both were previously absent from this lightweight list DTO entirely - since this is
        // what actually populates the frontend's directory-wide employee list (data.employees),
        // any chart/computation reading dateOfJoining or an exit date from it (e.g. the CEO
        // Dashboard's headcount-growth and attrition trends) always saw undefined for real data,
        // regardless of what the full single-employee EmployeeResponse carried.
        LocalDate dateOfJoining,
        LocalDate exitDate
) {
}
