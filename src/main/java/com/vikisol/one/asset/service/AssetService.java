package com.vikisol.one.asset.service;

import com.vikisol.one.asset.dto.*;
import com.vikisol.one.asset.entity.Asset;
import com.vikisol.one.asset.entity.AssetAssignment;
import com.vikisol.one.asset.repository.AssetAssignmentRepository;
import com.vikisol.one.asset.repository.AssetRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public AssetResponse createAsset(AssetRequest request) {
        int nextNumber = assetRepository.findMaxAssetTag() + 1;
        String assetTag = String.format("AST-%04d", nextNumber);

        Asset asset = Asset.builder()
                .assetTag(assetTag)
                .name(request.getName())
                .category(request.getCategory())
                .brand(request.getBrand())
                .model(request.getModel())
                .serialNumber(request.getSerialNumber())
                .purchaseDate(request.getPurchaseDate())
                .purchasePrice(request.getPurchasePrice())
                .warrantyEndDate(request.getWarrantyEndDate())
                .status(Asset.Status.AVAILABLE)
                .condition(request.getCondition() != null ? request.getCondition() : Asset.Condition.NEW)
                .location(request.getLocation())
                .notes(request.getNotes())
                .build();

        asset = assetRepository.save(asset);
        return mapToResponse(asset);
    }

    @Transactional
    public AssetResponse updateAsset(UUID id, AssetRequest request) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found"));

        asset.setName(request.getName());
        asset.setCategory(request.getCategory());
        asset.setBrand(request.getBrand());
        asset.setModel(request.getModel());
        asset.setSerialNumber(request.getSerialNumber());
        asset.setPurchaseDate(request.getPurchaseDate());
        asset.setPurchasePrice(request.getPurchasePrice());
        asset.setWarrantyEndDate(request.getWarrantyEndDate());
        if (request.getCondition() != null) asset.setCondition(request.getCondition());
        asset.setLocation(request.getLocation());
        asset.setNotes(request.getNotes());

        asset = assetRepository.save(asset);
        return mapToResponse(asset);
    }

    @Transactional(readOnly = true)
    public AssetResponse getAssetById(UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found"));
        return mapToResponse(asset);
    }

    @Transactional(readOnly = true)
    public Page<AssetResponse> getAllAssets(Pageable pageable) {
        return assetRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> getAvailableAssets() {
        return assetRepository.findByStatus(Asset.Status.AVAILABLE)
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public AssetAssignmentResponse assignAsset(AssetAssignmentRequest request, UserPrincipal principal) {
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Asset not found"));

        if (asset.getStatus() != Asset.Status.AVAILABLE) {
            throw new RuntimeException("Asset is not available for assignment");
        }

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Employee assigner = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Assigner not found"));

        asset.setStatus(Asset.Status.ASSIGNED);
        assetRepository.save(asset);

        AssetAssignment assignment = AssetAssignment.builder()
                .asset(asset)
                .employee(employee)
                .assignedDate(LocalDate.now())
                .assignedById(assigner.getId())
                .conditionAtAssignment(asset.getCondition())
                .remarks(request.getRemarks())
                .isActive(true)
                .build();

        assignment = assetAssignmentRepository.save(assignment);
        return mapToAssignmentResponse(assignment);
    }

    @Transactional
    public AssetAssignmentResponse returnAsset(AssetReturnRequest request, UserPrincipal principal) {
        AssetAssignment assignment = assetAssignmentRepository.findByAssetIdAndIsActiveTrue(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("No active assignment found for this asset"));

        Employee returner = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        assignment.setReturnDate(LocalDate.now());
        assignment.setReturnedById(returner.getId());
        assignment.setConditionAtReturn(request.getCondition());
        if (request.getRemarks() != null) {
            assignment.setRemarks(request.getRemarks());
        }
        assignment.setActive(false);
        assetAssignmentRepository.save(assignment);

        Asset asset = assignment.getAsset();
        asset.setStatus(Asset.Status.AVAILABLE);
        asset.setCondition(request.getCondition());
        assetRepository.save(asset);

        return mapToAssignmentResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<AssetAssignmentResponse> getEmployeeAssets(UUID employeeId) {
        return assetAssignmentRepository.findByEmployeeIdAndIsActiveTrue(employeeId)
                .stream().map(this::mapToAssignmentResponse).toList();
    }

    @Transactional
    public void deleteAsset(UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found"));
        if (asset.getStatus() == Asset.Status.ASSIGNED) {
            throw new RuntimeException("Cannot delete an assigned asset");
        }
        assetRepository.delete(asset);
    }

    private AssetResponse mapToResponse(Asset asset) {
        return AssetResponse.builder()
                .id(asset.getId())
                .assetTag(asset.getAssetTag())
                .name(asset.getName())
                .category(asset.getCategory())
                .brand(asset.getBrand())
                .model(asset.getModel())
                .serialNumber(asset.getSerialNumber())
                .purchaseDate(asset.getPurchaseDate())
                .purchasePrice(asset.getPurchasePrice())
                .warrantyEndDate(asset.getWarrantyEndDate())
                .status(asset.getStatus())
                .condition(asset.getCondition())
                .location(asset.getLocation())
                .notes(asset.getNotes())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }

    private AssetAssignmentResponse mapToAssignmentResponse(AssetAssignment assignment) {
        return AssetAssignmentResponse.builder()
                .id(assignment.getId())
                .assetId(assignment.getAsset().getId())
                .assetTag(assignment.getAsset().getAssetTag())
                .assetName(assignment.getAsset().getName())
                .employeeId(assignment.getEmployee().getId())
                .employeeName(assignment.getEmployee().getFirstName() + " " + assignment.getEmployee().getLastName())
                .assignedDate(assignment.getAssignedDate())
                .returnDate(assignment.getReturnDate())
                .conditionAtAssignment(assignment.getConditionAtAssignment())
                .conditionAtReturn(assignment.getConditionAtReturn())
                .remarks(assignment.getRemarks())
                .isActive(assignment.isActive())
                .createdAt(assignment.getCreatedAt())
                .build();
    }
}
