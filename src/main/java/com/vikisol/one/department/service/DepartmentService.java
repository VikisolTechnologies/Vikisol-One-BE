package com.vikisol.one.department.service;

import com.vikisol.one.department.dto.DepartmentRequest;
import com.vikisol.one.department.dto.DepartmentResponse;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        Department department = Department.builder()
                .name(request.name())
                .code(request.code())
                .description(request.description())
                .managerId(request.managerId())
                .isActive(true)
                .build();
        department = departmentRepository.save(department);
        return toResponse(department);
    }

    @Transactional
    public DepartmentResponse update(UUID id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        department.setName(request.name());
        department.setCode(request.code());
        department.setDescription(request.description());
        department.setManagerId(request.managerId());
        department = departmentRepository.save(department);
        return toResponse(department);
    }

    public DepartmentResponse getById(UUID id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        return toResponse(department);
    }

    public List<DepartmentResponse> getAll() {
        return departmentRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<DepartmentResponse> searchByName(String name) {
        return departmentRepository.findByNameContainingIgnoreCase(name).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        department.setActive(false);
        departmentRepository.save(department);
    }

    private DepartmentResponse toResponse(Department department) {
        String managerName = null;
        if (department.getManagerId() != null) {
            managerName = employeeRepository.findById(department.getManagerId())
                    .map(emp -> emp.getFirstName() + " " + emp.getLastName())
                    .orElse(null);
        }
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getCode(),
                department.getDescription(),
                managerName,
                department.isActive(),
                department.getCreatedAt()
        );
    }
}
