package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.EmployeeSkill;

import java.time.LocalDate;

public record SkillRequest(
        String skillName,
        Double yearsOfExperience,
        EmployeeSkill.Level level,
        LocalDate lastUsed,
        Boolean certified
) {
}
