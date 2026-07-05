package com.vikisol.one.employee.dto;

import java.util.List;

public record ProfileCompletionResponse(int percent, List<String> missing) {
}
