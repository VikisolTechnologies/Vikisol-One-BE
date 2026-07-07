package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String personalEmail,
        String personalMobile,
        LocalDate dateOfBirth,
        Employee.Gender gender,
        UUID departmentId,
        String departmentName,
        UUID designationId,
        String designationTitle,
        LocalDate dateOfJoining,
        LocalDate probationEndDate,
        LocalDate confirmationDate,
        UUID reportingManagerId,
        String reportingManagerName,
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
        BigDecimal ctc,
        boolean isActive,
        LocalDateTime createdAt,
        String accountRole,
        boolean onboardingDocumentsVerified,
        boolean onboardingAssetsAssigned,
        boolean onboardingBankDetailsCollected,
        boolean onboardingInductionCompleted,
        String nomineeName,
        String nomineeRelation,
        LocalDate nomineeDateOfBirth,
        Integer nomineeSharePercentage,
        Employee.Gender nomineeGender,
        String maritalStatus,
        String nationality,
        String bloodGroup,
        String languagesKnown,
        Employee.LifecycleStatus lifecycleStatus,
        String costCenter,
        String businessUnit,
        // Resolved from the employee's most recent OffboardingCase.lastWorkingDate - there is no
        // such field directly on Employee itself. Powers attrition/exit reporting (e.g. the CEO
        // Dashboard's "joined vs left per month" chart), which previously always read this as
        // null/undefined for real data since nothing populated it at all.
        LocalDate exitDate
) {
}
