package com.vikisol.one.hrtasks.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// One row in any HR Task Center category list - deliberately generic (rather than one DTO per
// category) since every category boils down to "which employee, why, and since/until when".
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrTaskItem {
    private UUID employeeId;
    private String employeeCode;
    private String employeeName;
    // Human-readable context, e.g. "Police Verification pending" or "Probation ends in 3 days".
    private String context;
    // ISO date string relevant to the item (dateOfJoining, probationEndDate, etc.), nullable.
    private String date;
}
