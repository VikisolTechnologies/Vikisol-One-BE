package com.vikisol.one.employee.service;

import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import com.vikisol.one.employee.dto.EmployeeListResponse;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.dto.HikeRequest;
import com.vikisol.one.employee.dto.ResignationRequest;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.payroll.service.PayrollService;
import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final UserRepository userRepository;
    private final PayrollService payrollService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Every employee needs a way to log in. If no userId was supplied and no account exists yet
     * for this email, provision one (defaulting to EMPLOYEE role) and email the temp password.
     * This is what makes "HR adds an employee" actually result in a usable login for 200+ staff,
     * instead of a profile record nobody can sign into.
     */
    private User resolveOrProvisionUser(EmployeeRequest request) {
        if (request.userId() != null) {
            return userRepository.findById(request.userId()).orElseThrow(() -> new RuntimeException("User not found"));
        }
        if (request.email() == null) {
            return null;
        }
        return userRepository.findByEmail(request.email()).orElseGet(() -> {
            String tempPassword = generateTempPassword();
            User newUser = User.builder()
                    .email(request.email())
                    .password(passwordEncoder.encode(tempPassword))
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .role(RoleEnum.EMPLOYEE)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build();
            newUser = userRepository.save(newUser);
            emailService.sendWelcomeEmail(request.email(), request.firstName() + " " + request.lastName(), tempPassword);
            return newUser;
        });
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        String nextEmployeeId = generateNextEmployeeId();

        Department department = request.departmentId() != null
                ? departmentRepository.findById(request.departmentId()).orElseThrow(() -> new RuntimeException("Department not found"))
                : null;
        Designation designation = request.designationId() != null
                ? designationRepository.findById(request.designationId()).orElseThrow(() -> new RuntimeException("Designation not found"))
                : null;
        User user = resolveOrProvisionUser(request);

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

    /**
     * Revises an employee's CTC using the CEO's standard breakup template and emails a hike letter.
     */
    @Transactional
    public EmployeeResponse issueHike(UUID id, HikeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        BigDecimal oldCtc = employee.getCtc();
        Map<String, BigDecimal> breakup = payrollService.computeCtcBreakup(request.newAnnualCtc());

        employee.setBasicSalary(breakup.get("basicSalary"));
        employee.setHra(breakup.get("hra"));
        employee.setConveyanceAllowance(breakup.get("conveyanceAllowance"));
        employee.setMedicalAllowance(breakup.get("medicalAllowance"));
        employee.setSpecialAllowance(breakup.get("specialAllowance"));
        employee.setGrossSalary(breakup.get("grossSalary"));
        employee.setCtc(breakup.get("ctc"));

        employee = employeeRepository.save(employee);

        emailService.sendHikeLetterEmail(
                employee.getEmail(),
                employee.getFirstName() + " " + employee.getLastName(),
                oldCtc,
                request.newAnnualCtc(),
                breakup,
                request.effectiveDate(),
                request.reason()
        );

        return toResponse(employee);
    }

    /**
     * Records a resignation, marks the employee ON_NOTICE, and emails an acknowledgement.
     */
    @Transactional
    public EmployeeResponse recordResignation(UUID id, ResignationRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employee.setEmploymentStatus(Employee.EmploymentStatus.ON_NOTICE);
        employee = employeeRepository.save(employee);

        emailService.sendResignationAcknowledgementEmail(
                employee.getEmail(),
                employee.getFirstName() + " " + employee.getLastName(),
                request.lastWorkingDate()
        );

        return toResponse(employee);
    }

    /**
     * Changes an employee's application login role (e.g. promoting EMPLOYEE -> MANAGER).
     * Only meaningful for employees who already have a linked login account.
     */
    @Transactional
    public EmployeeResponse changeAccountRole(UUID id, RoleEnum newRole) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (employee.getUser() == null) {
            throw new RuntimeException("This employee has no login account to change the role of");
        }
        User user = employee.getUser();
        user.setRole(newRole);
        userRepository.save(user);
        return toResponse(employee);
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
                employee.getCreatedAt(),
                employee.getUser() != null ? employee.getUser().getRole().name() : null
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
