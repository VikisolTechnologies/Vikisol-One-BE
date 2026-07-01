package com.vikisol.one.asset.dto;

import com.vikisol.one.asset.entity.Asset;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AssetResponse {

    private UUID id;
    private String assetTag;
    private String name;
    private Asset.Category category;
    private String brand;
    private String model;
    private String serialNumber;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private LocalDate warrantyEndDate;
    private Asset.Status status;
    private Asset.Condition condition;
    private String location;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
