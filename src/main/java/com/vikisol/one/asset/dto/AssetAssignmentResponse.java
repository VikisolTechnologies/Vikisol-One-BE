package com.vikisol.one.asset.dto;

import com.vikisol.one.asset.entity.Asset;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AssetAssignmentResponse {

    private UUID id;
    private UUID assetId;
    private String assetTag;
    private String assetName;
    private UUID employeeId;
    private String employeeName;
    private LocalDate assignedDate;
    private LocalDate returnDate;
    private Asset.Condition conditionAtAssignment;
    private Asset.Condition conditionAtReturn;
    private String remarks;
    private boolean isActive;
    private LocalDateTime createdAt;
}
