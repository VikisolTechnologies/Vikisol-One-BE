package com.vikisol.one.employee.dto;

public record EducationRequest(
        String degree,
        String university,
        String college,
        Integer yearOfCompletion,
        String gradeOrPercentage,
        String certificateDocumentUrl
) {
}
