package com.vikisol.one.asset.controller;

import com.vikisol.one.asset.dto.*;
import com.vikisol.one.asset.service.AssetService;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(@Valid @RequestBody AssetRequest request) {
        AssetResponse response = assetService.createAsset(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Asset created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<AssetResponse>> updateAsset(
            @PathVariable UUID id, @Valid @RequestBody AssetRequest request) {
        AssetResponse response = assetService.updateAsset(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Asset updated successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetResponse>> getAssetById(@PathVariable UUID id) {
        AssetResponse response = assetService.getAssetById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Asset fetched", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER', 'CEO')")
    public ResponseEntity<ApiResponse<PagedResponse<AssetResponse>>> getAllAssets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AssetResponse> assets = assetService.getAllAssets(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PagedResponse<AssetResponse> pagedResponse = new PagedResponse<>(
                assets.getContent(), assets.getNumber(), assets.getSize(),
                assets.getTotalElements(), assets.getTotalPages(), assets.isLast());
        return ResponseEntity.ok(new ApiResponse<>(true, "Assets fetched", pagedResponse));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getAvailableAssets() {
        List<AssetResponse> assets = assetService.getAvailableAssets();
        return ResponseEntity.ok(new ApiResponse<>(true, "Available assets fetched", assets));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<AssetAssignmentResponse>> assignAsset(
            @Valid @RequestBody AssetAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AssetAssignmentResponse response = assetService.assignAsset(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Asset assigned successfully", response));
    }

    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<AssetAssignmentResponse>> returnAsset(
            @Valid @RequestBody AssetReturnRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AssetAssignmentResponse response = assetService.returnAsset(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Asset returned successfully", response));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<AssetAssignmentResponse>>> getEmployeeAssets(
            @PathVariable UUID employeeId) {
        List<AssetAssignmentResponse> assignments = assetService.getEmployeeAssets(employeeId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee assets fetched", assignments));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        assetService.deleteAsset(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Asset deleted successfully", null));
    }
}
