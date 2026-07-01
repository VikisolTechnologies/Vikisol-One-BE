package com.vikisol.one.employee.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.employee.dto.EmployeeListResponse;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.service.EmployeeService;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeListResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<EmployeeListResponse> employees = employeeService.getAll(pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employees retrieved", employees));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getById(@PathVariable UUID id) {
        EmployeeResponse employee = employeeService.getById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee retrieved", employee));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        EmployeeResponse employee = employeeService.getProfile(userPrincipal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile retrieved", employee));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(@RequestBody EmployeeRequest request) {
        EmployeeResponse employee = employeeService.createEmployee(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee created", employee));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(@PathVariable UUID id, @RequestBody EmployeeRequest request) {
        EmployeeResponse employee = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee updated", employee));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        employeeService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee deactivated", null));
    }

    @GetMapping("/department/{deptId}")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeListResponse>>> getByDepartment(
            @PathVariable UUID deptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<EmployeeListResponse> employees = employeeService.getByDepartment(deptId, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employees retrieved", employees));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeListResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<EmployeeListResponse> employees = employeeService.search(q, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Search results", employees));
    }

    @GetMapping("/reporting/{managerId}")
    public ResponseEntity<ApiResponse<List<EmployeeListResponse>>> getByReportingManager(@PathVariable UUID managerId) {
        List<EmployeeListResponse> employees = employeeService.getByReportingManager(managerId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Direct reports retrieved", employees));
    }
}
