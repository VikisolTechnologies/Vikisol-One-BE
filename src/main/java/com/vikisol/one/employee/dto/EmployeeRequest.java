package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeRequest(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String personalEmail,
        String personalMobile,
        LocalDate dateOfBirth,
        Employee.Gender gender,
        UUID departmentId,
        UUID designationId,
        LocalDate dateOfJoining,
        LocalDate probationEndDate,
        LocalDate confirmationDate,
        UUID reportingManagerId,
        Employee.EmploymentType employmentType,
        Employee.EmploymentStatus employmentStatus,
        String currentAddress,
        String permanentAddress,
        String city,
        String state,
        String country,
        String pincode,
        String bankName,
        String bankAccountNumber,
        String ifscCode,
        String panNumber,
        String aadharNumber,
        String uanNumber,
        String pfNumber,
        String esiNumber,
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelation,
        String profilePictureUrl,
        BigDecimal basicSalary,
        BigDecimal hra,
        BigDecimal conveyanceAllowance,
        BigDecimal medicalAllowance,
        BigDecimal specialAllowance,
        BigDecimal customAllowance,
        BigDecimal grossSalary,
        BigDecimal ctc
) {
}
