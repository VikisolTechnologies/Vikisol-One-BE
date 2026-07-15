package com.vikisol.one.employee.dto;

import java.util.List;

public record BulkImportResult(int created, int failed, List<String> errors) {}
