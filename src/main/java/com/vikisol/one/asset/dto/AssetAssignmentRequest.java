package com.vikisol.one.asset.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssetAssignmentRequest {

    @NotNull
    private UUID assetId;

    @NotNull
    private UUID employeeId;

    private String remarks;
}
