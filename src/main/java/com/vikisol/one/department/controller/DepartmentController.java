package com.vikisol.one.department.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.department.dto.DepartmentRequest;
import com.vikisol.one.department.dto.DepartmentResponse;
import com.vikisol.one.department.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAll() {
        List<DepartmentResponse> departments = departmentService.getAll();
        return ResponseEntity.ok(new ApiResponse<>(true, "Departments retrieved", departments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getById(@PathVariable UUID id) {
        DepartmentResponse department = departmentService.getById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Department retrieved", department));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(@RequestBody DepartmentRequest request) {
        DepartmentResponse department = departmentService.create(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Department created", department));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(@PathVariable UUID id, @RequestBody DepartmentRequest request) {
        DepartmentResponse department = departmentService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Department updated", department));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        departmentService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Department deactivated", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> search(@RequestParam String q) {
        List<DepartmentResponse> departments = departmentService.searchByName(q);
        return ResponseEntity.ok(new ApiResponse<>(true, "Search results", departments));
    }
}
