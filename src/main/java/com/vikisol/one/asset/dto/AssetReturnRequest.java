package com.vikisol.one.asset.dto;

import com.vikisol.one.asset.entity.Asset;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssetReturnRequest {

    @NotNull
    private UUID assetId;

    @NotNull
    private Asset.Condition condition;

    private String remarks;
}
