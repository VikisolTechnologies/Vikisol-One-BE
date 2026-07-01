package com.vikisol.one.resource.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.resource.dto.ResourceRequest;
import com.vikisol.one.resource.dto.ResourceResponse;
import com.vikisol.one.resource.entity.Resource;
import com.vikisol.one.resource.service.ResourceService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getAllResources() {
        List<ResourceResponse> resources = resourceService.getAllResources();
        return ResponseEntity.ok(new ApiResponse<>(true, "Resources retrieved successfully", resources));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResourceResponse>> getResourceById(@PathVariable UUID id) {
        ResourceResponse resource = resourceService.getResourceById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Resource retrieved successfully", resource));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ResourceResponse>> createResource(
            @Valid @RequestBody ResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ResourceResponse resource = resourceService.createResource(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Resource created successfully", resource));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ResourceResponse>> updateResource(
            @PathVariable UUID id,
            @Valid @RequestBody ResourceRequest request) {
        ResourceResponse resource = resourceService.updateResource(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Resource updated successfully", resource));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable UUID id) {
        resourceService.deleteResource(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Resource deleted successfully", null));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getByCategory(
            @PathVariable Resource.ResourceCategory category) {
        List<ResourceResponse> resources = resourceService.getResourcesByCategory(category);
        return ResponseEntity.ok(new ApiResponse<>(true, "Resources retrieved successfully", resources));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> searchResources(@RequestParam String q) {
        List<ResourceResponse> resources = resourceService.searchResources(q);
        return ResponseEntity.ok(new ApiResponse<>(true, "Resources retrieved successfully", resources));
    }
}
