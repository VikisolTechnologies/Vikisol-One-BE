package com.vikisol.one.designation.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.designation.dto.DesignationRequest;
import com.vikisol.one.designation.dto.DesignationResponse;
import com.vikisol.one.designation.service.DesignationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/designations")
@RequiredArgsConstructor
public class DesignationController {

    private final DesignationService designationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DesignationResponse>>> getAll() {
        List<DesignationResponse> designations = designationService.getAll();
        return ResponseEntity.ok(new ApiResponse<>(true, "Designations retrieved", designations));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DesignationResponse>> getById(@PathVariable UUID id) {
        DesignationResponse designation = designationService.getById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Designation retrieved", designation));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<DesignationResponse>> create(@RequestBody DesignationRequest request) {
        DesignationResponse designation = designationService.create(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Designation created", designation));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<DesignationResponse>> update(@PathVariable UUID id, @RequestBody DesignationRequest request) {
        DesignationResponse designation = designationService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Designation updated", designation));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        designationService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Designation deactivated", null));
    }
}
