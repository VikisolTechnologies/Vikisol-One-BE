package com.vikisol.one.report.dto;

import java.time.LocalDate;
import java.util.Map;

public record HeadcountReportResponse(
        LocalDate date,
        long totalHeadcount,
        Map<String, Long> departmentWise,
        Map<String, Long> designationWise,
        long newJoinees,
        long exits
) {}
