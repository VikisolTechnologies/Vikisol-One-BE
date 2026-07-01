package com.vikisol.one.timesheet.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record TimesheetSubmitRequest(@NotEmpty List<UUID> entryIds) {}
