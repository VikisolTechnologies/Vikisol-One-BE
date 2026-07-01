package com.vikisol.one.employee.service;

import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import com.vikisol.one.employee.dto.EmployeeListResponse;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final UserRepository userRepository;

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        String nextEmployeeId = generateNextEmployeeId();

        Department department = request.departmentId() != null
                ? departmentRepository.findById(request.departmentId()).orElseThrow(() -> new RuntimeException("Department not found"))
                : null;
        Designation designation = request.designationId() != null
                ? designationRepository.findById(request.designationId()).orElseThrow(() -> new RuntimeException("Designation not found"))
                : null;
        User user = request.userId() != null
                ? userRepository.findById(request.userId()).orElseThrow(() -> new RuntimeException("User not found"))
                : null;

        Employee employee = Employee.builder()
                .employeeId(nextEmployeeId)
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .department(department)
                .designation(designation)
                .dateOfJoining(request.dateOfJoining())
                .probationEndDate(request.probationEndDate())
                .confirmationDate(request.confirmationDate())
                .reportingManagerId(request.reportingManagerId())
                .employmentType(request.employmentType())
                .employmentStatus(request.employmentStatus() != null ? request.employmentStatus() : Employee.EmploymentStatus.ACTIVE)
                .currentAddress(request.currentAddress())
                .permanentAddress(request.permanentAddress())
                .city(request.city())
                .state(request.state())
                .country(request.country())
                .pincode(request.pincode())
                .bankName(request.bankName())
                .bankAccountNumber(request.bankAccountNumber())
                .ifscCode(request.ifscCode())
                .panNumber(request.panNumber())
                .aadharNumber(request.aadharNumber())
                .uanNumber(request.uanNumber())
                .pfNumber(request.pfNumber())
                .esiNumber(request.esiNumber())
                .emergencyContactName(request.emergencyContactName())
                .emergencyContactPhone(request.emergencyContactPhone())
                .emergencyContactRelation(request.emergencyContactRelation())
                .profilePictureUrl(request.profilePictureUrl())
                .basicSalary(request.basicSalary())
                .hra(request.hra())
                .conveyanceAllowance(request.conveyanceAllowance())
                .medicalAllowance(request.medicalAllowance())
                .specialAllowance(request.specialAllowance())
                .grossSalary(request.grossSalary())
                .ctc(request.ctc())
                .isActive(true)
                .build();

        employee = employeeRepository.save(employee);
        return toResponse(employee);
    }

    @Transactional
    public EmployeeResponse updateEmployee(UUID id, EmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Department department = request.departmentId() != null
                ? departmentRepository.findById(request.departmentId()).orElseThrow(() -> new RuntimeException("Department not found"))
                : null;
        Designation designation = request.designationId() != null
                ? designationRepository.findById(request.designationId()).orElseThrow(() -> new RuntimeException("Designation not found"))
                : null;

        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(request.gender());
        employee.setDepartment(department);
        employee.setDesignation(designation);
        employee.setDateOfJoining(request.dateOfJoining());
        employee.setProbationEndDate(request.probationEndDate());
        employee.setConfirmationDate(request.confirmationDate());
        employee.setReportingManagerId(request.reportingManagerId());
        employee.setEmploymentType(request.employmentType());
        employee.setEmploymentStatus(request.employmentStatus());
        employee.setCurrentAddress(request.currentAddress());
        employee.setPermanentAddress(request.permanentAddress());
        employee.setCity(request.city());
        employee.setState(request.state());
        employee.setCountry(request.country());
        employee.setPincode(request.pincode());
        employee.setBankName(request.bankName());
        employee.setBankAccountNumber(request.bankAccountNumber());
        employee.setIfscCode(request.ifscCode());
        employee.setPanNumber(request.panNumber());
        employee.setAadharNumber(request.aadharNumber());
        employee.setUanNumber(request.uanNumber());
        employee.setPfNumber(request.pfNumber());
        employee.setEsiNumber(request.esiNumber());
        employee.setEmergencyContactName(request.emergencyContactName());
        employee.setEmergencyContactPhone(request.emergencyContactPhone());
        employee.setEmergencyContactRelation(request.emergencyContactRelation());
        employee.setProfilePictureUrl(request.profilePictureUrl());
        employee.setBasicSalary(request.basicSalary());
        employee.setHra(request.hra());
        employee.setConveyanceAllowance(request.conveyanceAllowance());
        employee.setMedicalAllowance(request.medicalAllowance());
        employee.setSpecialAllowance(request.specialAllowance());
        employee.setGrossSalary(request.grossSalary());
        employee.setCtc(request.ctc());

        employee = employeeRepository.save(employee);
        return toResponse(employee);
    }

    public EmployeeResponse getById(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        return toResponse(employee);
    }

    public PagedResponse<EmployeeListResponse> getAll(Pageable pageable) {
        Page<Employee> page = employeeRepository.findByIsActiveTrue(pageable);
        return toPagedResponse(page);
    }

    public PagedResponse<EmployeeListResponse> getByDepartment(UUID departmentId, Pageable pageable) {
        Page<Employee> page = employeeRepository.findByDepartmentId(departmentId, pageable);
        return toPagedResponse(page);
    }

    public PagedResponse<EmployeeListResponse> search(String query, Pageable pageable) {
        Page<Employee> page = employeeRepository.searchByName(query, pageable);
        return toPagedResponse(page);
    }

    public List<EmployeeListResponse> getByReportingManager(UUID managerId) {
        return employeeRepository.findByReportingManagerId(managerId).stream()
                .map(this::toListResponse)
                .toList();
    }

    public EmployeeResponse getProfile(UserPrincipal userPrincipal) {
        Employee employee = employeeRepository.findByUserId(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Employee profile not found"));
        return toResponse(employee);
    }

    @Transactional
    public void deactivate(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        employee.setActive(false);
        employeeRepository.save(employee);
    }

    private String generateNextEmployeeId() {
        List<Employee> all = employeeRepository.findAll();
        int maxNum = all.stream()
                .map(Employee::getEmployeeId)
                .filter(eid -> eid != null && eid.startsWith("VIK-"))
                .map(eid -> eid.substring(4))
                .mapToInt(num -> {
                    try { return Integer.parseInt(num); } catch (NumberFormatException e) { return 0; }
                })
                .max()
                .orElse(0);
        return String.format("VIK-%04d", maxNum + 1);
    }

    private EmployeeResponse toResponse(Employee employee) {
        String departmentName = employee.getDepartment() != null ? employee.getDepartment().getName() : null;
        String designationTitle = employee.getDesignation() != null ? employee.getDesignation().getTitle() : null;
        String reportingManagerName = null;
        if (employee.getReportingManagerId() != null) {
            reportingManagerName = employeeRepository.findById(employee.getReportingManagerId())
                    .map(mgr -> mgr.getFirstName() + " " + mgr.getLastName())
                    .orElse(null);
        }

        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getDateOfBirth(),
                employee.getGender(),
                employee.getDepartment() != null ? employee.getDepartment().getId() : null,
                departmentName,
                employee.getDesignation() != null ? employee.getDesignation().getId() : null,
                designationTitle,
                employee.getDateOfJoining(),
                employee.getProbationEndDate(),
                employee.getConfirmationDate(),
                employee.getReportingManagerId(),
                reportingManagerName,
                employee.getEmploymentType(),
                employee.getEmploymentStatus(),
                employee.getCurrentAddress(),
                employee.getPermanentAddress(),
                employee.getCity(),
                employee.getState(),
                employee.getCountry(),
                employee.getPincode(),
                employee.getBankName(),
                employee.getBankAccountNumber(),
                employee.getIfscCode(),
                employee.getPanNumber(),
                employee.getAadharNumber(),
                employee.getUanNumber(),
                employee.getPfNumber(),
                employee.getEsiNumber(),
                employee.getEmergencyContactName(),
                employee.getEmergencyContactPhone(),
                employee.getEmergencyContactRelation(),
                employee.getProfilePictureUrl(),
                employee.getBasicSalary(),
                employee.getHra(),
                employee.getConveyanceAllowance(),
                employee.getMedicalAllowance(),
                employee.getSpecialAllowance(),
                employee.getGrossSalary(),
                employee.getCtc(),
                employee.isActive(),
                employee.getCreatedAt()
        );
    }

    private EmployeeListResponse toListResponse(Employee employee) {
        return new EmployeeListResponse(
                employee.getId(),
                employee.getEmployeeId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getDepartment() != null ? employee.getDepartment().getName() : null,
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                employee.getEmploymentStatus(),
                employee.getProfilePictureUrl()
        );
    }

    private PagedResponse<EmployeeListResponse> toPagedResponse(Page<Employee> page) {
        List<EmployeeListResponse> content = page.getContent().stream()
                .map(this::toListResponse)
                .toList();
        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
