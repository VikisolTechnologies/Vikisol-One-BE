package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.EmployeeSkill;

import java.time.LocalDate;
import java.util.UUID;

public record SkillResponse(
        UUID id,
        String skillName,
        Double yearsOfExperience,
        EmployeeSkill.Level level,
        LocalDate lastUsed,
        boolean certified
) {
}
