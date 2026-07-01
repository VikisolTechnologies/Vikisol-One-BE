package com.vikisol.one.asset.dto;

import com.vikisol.one.asset.entity.Asset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AssetRequest {

    @NotBlank
    private String name;

    @NotNull
    private Asset.Category category;

    private String brand;
    private String model;
    private String serialNumber;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private LocalDate warrantyEndDate;
    private Asset.Condition condition;
    private String location;
    private String notes;
}
