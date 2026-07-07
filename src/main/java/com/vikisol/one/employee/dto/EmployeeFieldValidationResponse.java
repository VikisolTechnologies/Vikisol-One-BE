package com.vikisol.one.employee.dto;

public record EmployeeFieldValidationResponse(
        boolean employeeIdExists,
        boolean officialEmailExists,
        boolean personalEmailExists,
        boolean mobileExists,
        boolean panExists,
        boolean aadhaarExists,
        boolean pfExists,
        boolean uanExists
) {}
