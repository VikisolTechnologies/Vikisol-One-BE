package com.vikisol.one.employee.dto;

import java.util.List;

public record ProfileCompletionResponse(int percent, List<String> missing, List<SectionStatus> sections) {
    public record SectionStatus(String key, String label, boolean done) {}
}
