package com.vikisol.one.employee.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.auth.entity.ActivationToken;
import com.vikisol.one.auth.repository.ActivationTokenRepository;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.common.service.FileModule;
import com.vikisol.one.common.service.FileStorageService;
import com.vikisol.one.common.service.PdfService;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import com.vikisol.one.doctemplate.service.DocumentGenerationService;
import com.vikisol.one.document.dto.DocumentUploadRequest;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.document.service.DocumentService;
import com.vikisol.one.employee.dto.EmployeeListResponse;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.dto.HikeRequest;
import com.vikisol.one.employee.dto.ManagerOptionResponse;
import com.vikisol.one.employee.dto.OnboardingChecklistRequest;
import com.vikisol.one.employee.dto.ResignationRequest;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.leave.service.LeaveService;
import com.vikisol.one.offboarding.dto.InitiateOffboardingRequest;
import com.vikisol.one.offboarding.entity.OffboardingCase;
import com.vikisol.one.offboarding.service.OffboardingService;
import com.vikisol.one.payroll.service.PayrollService;
import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final PayrollService payrollService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LeaveService leaveService;
    private final PdfService pdfService;
    private final FileStorageService fileStorageService;
    private final DocumentService documentService;
    private final DocumentGenerationService documentGenerationService;
    private final com.vikisol.one.settings.service.BrandingService brandingService;
    private final OffboardingService offboardingService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Alphanumeric only - this same generator also produces the activation-link token, which gets
    // embedded raw (unencoded) into a URL as `?token=...`. A `#` starts the URL fragment and
    // silently truncates the query string there, and `&`/`+`/`%` etc. have their own query-string
    // meaning - any of the old charset's `!@#$` could corrupt the link. Real bug, confirmed live:
    // an activation link containing `#` and `$` was rejected as "invalid" because the frontend
    // only ever received the token text before the `#`.
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration ACTIVATION_TOKEN_TTL = Duration.ofHours(24);

    private String generateRandomToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Every employee needs a way to log in. If no userId was supplied and no account exists yet
     * for this email, provision one (defaulting to EMPLOYEE role) - disabled, with an unusable
     * random password - and email an activation link to their PERSONAL address so they can set
     * their own password. No password is ever emailed under this flow (see sendActivationEmail).
     */
    private User resolveOrProvisionUser(EmployeeRequest request) {
        if (request.userId() != null) {
            return userRepository.findById(request.userId()).orElseThrow(() -> new RuntimeException("User not found"));
        }
        if (request.email() == null) {
            return null;
        }
        return userRepository.findByEmail(request.email()).orElseGet(() -> {
            User newUser = User.builder()
                    .email(request.email())
                    .password(passwordEncoder.encode(generateRandomToken()))
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .role(RoleEnum.EMPLOYEE)
                    .enabled(false)
                    .accountNonLocked(true)
                    .build();
            newUser = userRepository.save(newUser);
            issueActivationToken(newUser, request.personalEmail() != null ? request.personalEmail() : request.email());
            return newUser;
        });
    }

    private void issueActivationToken(User user, String sendTo) {
        String token = generateRandomToken();
        ActivationToken activationToken = ActivationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(Instant.now().plus(ACTIVATION_TOKEN_TTL))
                .used(false)
                .build();
        activationTokenRepository.save(activationToken);
        String activationLink = frontendUrl + "/activate?token=" + token;
        emailService.sendActivationEmail(sendTo, user.getFirstName() + " " + user.getLastName(), activationLink);
    }

    // Backs the Add Employee form's real-time inline validation (GET /employees/validate) so a
    // raw DB unique-constraint violation is never the first time HR learns a value is taken.
    // Also called defensively at the start of createEmployee - closes the TOCTOU gap only
    // partially (a true race between two concurrent submits still relies on the DB constraint +
    // GlobalExceptionHandler's DataIntegrityViolationException handler as the final backstop),
    // but eliminates it for the overwhelmingly common case of a single HR user re-checking values
    // that were already taken before they ever click Submit.
    @Transactional(readOnly = true)
    public com.vikisol.one.employee.dto.EmployeeFieldValidationResponse validateFields(
            String employeeId, String officialEmail, String personalEmail, String mobile,
            String pan, String aadhaar, String pf, String uan) {
        return new com.vikisol.one.employee.dto.EmployeeFieldValidationResponse(
                employeeId != null && !employeeId.isBlank() && employeeRepository.existsByEmployeeId(employeeId),
                officialEmail != null && !officialEmail.isBlank()
                        && (employeeRepository.existsByEmailIgnoreCase(officialEmail) || userRepository.existsByEmail(officialEmail.trim().toLowerCase())),
                personalEmail != null && !personalEmail.isBlank() && employeeRepository.existsByPersonalEmailIgnoreCase(personalEmail),
                mobile != null && !mobile.isBlank() && employeeRepository.existsByPersonalMobile(mobile),
                pan != null && !pan.isBlank() && employeeRepository.existsByPanNumberIgnoreCase(pan),
                aadhaar != null && !aadhaar.isBlank() && employeeRepository.existsByAadharNumber(aadhaar),
                pf != null && !pf.isBlank() && employeeRepository.existsByPfNumber(pf),
                uan != null && !uan.isBlank() && employeeRepository.existsByUanNumber(uan)
        );
    }

    private void assertFieldsNotTaken(EmployeeRequest request) {
        var v = validateFields(null, request.email(), request.personalEmail(), request.personalMobile(),
                request.panNumber(), request.aadharNumber(), request.pfNumber(), request.uanNumber());
        if (v.officialEmailExists()) throw new RuntimeException("This official email address is already in use");
        if (v.personalEmailExists()) throw new RuntimeException("This personal email address is already in use");
        if (v.mobileExists()) throw new RuntimeException("This personal mobile number is already in use");
        if (v.panExists()) throw new RuntimeException("This PAN number is already in use");
        if (v.aadhaarExists()) throw new RuntimeException("This Aadhaar number is already in use");
        if (v.pfExists()) throw new RuntimeException("This PF number is already in use");
        if (v.uanExists()) throw new RuntimeException("This UAN is already in use");
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        assertFieldsNotTaken(request);
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
                .personalEmail(request.personalEmail())
                .personalMobile(request.personalMobile())
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
                .customAllowance(request.customAllowance())
                .grossSalary(request.grossSalary())
                .ctc(request.ctc())
                .nomineeName(request.nomineeName())
                .nomineeRelation(request.nomineeRelation())
                .nomineeDateOfBirth(request.nomineeDateOfBirth())
                .nomineeSharePercentage(request.nomineeSharePercentage())
                .nomineeGender(request.nomineeGender())
                .maritalStatus(request.maritalStatus())
                .nationality(request.nationality())
                .bloodGroup(request.bloodGroup())
                .languagesKnown(request.languagesKnown())
                .isActive(true)
                // Candidate-to-employee conversion (RecruitmentService.approveSelection) and direct
                // HR-created employees both land here. If the join date is still in the future there's
                // a pre-boarding gap to track; otherwise they're starting immediately, so ACTIVE.
                .lifecycleStatus(request.dateOfJoining() != null && request.dateOfJoining().isAfter(java.time.LocalDate.now())
                        ? Employee.LifecycleStatus.PRE_BOARDING
                        : Employee.LifecycleStatus.ACTIVE)
                .build();

        employee = employeeRepository.save(employee);
        // Without this, a mid-year hire has zero leave balance until the next Jan 1 catch-up -
        // they'd hit the same "Leave balance not found" error found via live workflow testing.
        leaveService.initializeLeaveBalances(employee.getId(), java.time.LocalDate.now().getYear());
        auditService.record("Employee Created", nextEmployeeId,
                employee.getFirstName() + " " + employee.getLastName() + " (" + employee.getEmail() + ")");
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
        employee.setPersonalEmail(request.personalEmail());
        employee.setPersonalMobile(request.personalMobile());
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
        employee.setCustomAllowance(request.customAllowance());
        employee.setGrossSalary(request.grossSalary());
        employee.setCtc(request.ctc());
        employee.setNomineeName(request.nomineeName());
        employee.setNomineeRelation(request.nomineeRelation());
        employee.setNomineeDateOfBirth(request.nomineeDateOfBirth());
        employee.setNomineeSharePercentage(request.nomineeSharePercentage());
        employee.setNomineeGender(request.nomineeGender());
        employee.setMaritalStatus(request.maritalStatus());
        employee.setNationality(request.nationality());
        employee.setBloodGroup(request.bloodGroup());
        employee.setLanguagesKnown(request.languagesKnown());

        employee = employeeRepository.save(employee);
        auditService.record("Employee Updated", employee.getEmployeeId(),
                employee.getFirstName() + " " + employee.getLastName());
        return toResponse(employee);
    }

    // Self-service profile update (Onboarding Wizard's Personal/Bank/Tax/Nominee steps) - deliberately
    // ignores admin-only fields (department, designation, employmentType/Status, salary components,
    // reportingManagerId, dateOfJoining, official email/phone) even though the frontend sends the
    // full merged profile object in the same request shape as updateEmployee above. This is enforced
    // here, not just by hiding the fields in the UI - an employee tampering the request body client-side
    // still can't move their own department/designation/CTC/employment status through this endpoint.
    @Transactional
    public EmployeeResponse updateOwnProfile(UUID id, EmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employee.setPersonalEmail(request.personalEmail());
        employee.setPersonalMobile(request.personalMobile());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(request.gender());
        employee.setCurrentAddress(request.currentAddress());
        employee.setPermanentAddress(request.permanentAddress());
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
        employee.setNomineeName(request.nomineeName());
        employee.setNomineeRelation(request.nomineeRelation());
        employee.setNomineeDateOfBirth(request.nomineeDateOfBirth());
        employee.setNomineeSharePercentage(request.nomineeSharePercentage());
        employee.setNomineeGender(request.nomineeGender());
        employee.setMaritalStatus(request.maritalStatus());
        employee.setNationality(request.nationality());
        employee.setBloodGroup(request.bloodGroup());
        employee.setLanguagesKnown(request.languagesKnown());

        employee = employeeRepository.save(employee);
        auditService.record("Employee Self-Updated Profile", employee.getEmployeeId(),
                employee.getFirstName() + " " + employee.getLastName());
        return toResponse(employee);
    }

    public EmployeeResponse getById(UUID id, UserPrincipal principal) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        EmployeeResponse response = toResponse(employee);

        boolean isPrivileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSelf = employee.getUser() != null && employee.getUser().getId().equals(principal.getId());

        return (isPrivileged || isSelf) ? response : maskSensitiveFields(response);
    }

    // Directory browsing (any authenticated role) should only ever see non-sensitive fields for
    // someone else's record - bank details, government IDs, and compensation are HR/CEO/self-only.
    private EmployeeResponse maskSensitiveFields(EmployeeResponse r) {
        return new EmployeeResponse(
                r.id(), r.employeeId(), r.firstName(), r.lastName(), r.email(), r.phone(),
                null, null, // personalEmail/personalMobile - PII, not directory-safe
                null, null, // dateOfBirth/gender
                r.departmentId(), r.departmentName(), r.designationId(), r.designationTitle(),
                r.dateOfJoining(), null, null,
                r.reportingManagerId(), r.reportingManagerName(),
                r.employmentType(), r.employmentStatus(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null,
                r.profilePictureUrl(),
                null, null, null, null, null, null, null, null,
                r.isActive(), r.createdAt(), r.accountRole(),
                r.onboardingDocumentsVerified(), r.onboardingAssetsAssigned(),
                r.onboardingBankDetailsCollected(), r.onboardingInductionCompleted(),
                null, null, null, null, null, null, null, null, null,
                r.lifecycleStatus(), r.costCenter(), r.businessUnit()
        );
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

    // CEO/HR resets an existing employee's login: disables the account and issues a fresh
    // activation link (to their personal email, same as first-login provisioning) rather than
    // emailing a new password - keeps "no password is ever emailed" true for every reset path,
    // not just initial account creation.
    @Transactional
    public void resetPassword(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        User user = employee.getUser();
        if (user == null) {
            throw new RuntimeException("This employee has no login account to reset");
        }
        user.setPassword(passwordEncoder.encode(generateRandomToken()));
        user.setEnabled(false);
        userRepository.save(user);
        issueActivationToken(user, employee.getPersonalEmail() != null ? employee.getPersonalEmail() : employee.getEmail());
        auditService.record("Password Reset", employee.getEmployeeId(),
                "Password reset for " + employee.getFirstName() + " " + employee.getLastName());
    }

    // Generates (or regenerates) an existing employee's official offer letter PDF from their
    // current, approved record - designation, CTC breakup, joining date, reporting manager - and
    // stores it as a document. Uses the same Document Studio engine + OfferLetterFieldsHelper as
    // RecruitmentService.approveSelection(), replacing the previous hardcoded
    // EmailService.buildOfferLetterPdfHtml() bypass of the template engine.
    public String generateOfferLetter(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (employee.getCtc() == null) {
            throw new RuntimeException("This employee has no CTC/salary details on file yet");
        }

        BigDecimal basic = nvl(employee.getBasicSalary());
        BigDecimal hra = nvl(employee.getHra());
        BigDecimal conveyance = nvl(employee.getConveyanceAllowance());
        BigDecimal medical = nvl(employee.getMedicalAllowance());
        BigDecimal special = nvl(employee.getSpecialAllowance());
        BigDecimal custom = nvl(employee.getCustomAllowance());
        Map<String, BigDecimal> breakup = Map.of(
                "basicSalary", basic,
                "hra", hra,
                "conveyanceAllowance", conveyance,
                "medicalAllowance", medical,
                "specialAllowance", special,
                "customAllowance", custom,
                "grossSalary", basic.add(hra).add(conveyance).add(medical).add(special).add(custom)
        );

        String reportingManagerTitle = null;
        String reportingManagerName = null;
        if (employee.getReportingManagerId() != null) {
            Employee manager = employeeRepository.findById(employee.getReportingManagerId()).orElse(null);
            if (manager != null) {
                reportingManagerName = manager.getFirstName() + " " + manager.getLastName();
                reportingManagerTitle = manager.getDesignation() != null ? manager.getDesignation().getTitle() : null;
            }
        }

        String fullName = employee.getFirstName() + " " + employee.getLastName();
        String designationTitle = employee.getDesignation() != null ? employee.getDesignation().getTitle() : "";
        String salutation = employee.getGender() == Employee.Gender.FEMALE ? "Ms."
                : employee.getGender() == Employee.Gender.MALE ? "Mr." : null;
        BigDecimal pt = payrollService.getConfigAsBigDecimal("PROFESSIONAL_TAX");

        Map<String, String> fields = com.vikisol.one.doctemplate.service.OfferLetterFieldsHelper.build(
                fullName, designationTitle, salutation,
                employee.getDateOfJoining() != null ? employee.getDateOfJoining() : java.time.LocalDate.now(),
                reportingManagerTitle, reportingManagerName,
                breakup, employee.getCtc(), pt, brandingService.getBranding());

        byte[] pdf = documentGenerationService.render(Document.DocumentType.OFFER_LETTER, fields);

        String storageFileName = "Offer_Letter_" + employee.getEmployeeId() + ".pdf";
        String downloadFileName = "Offer_Letter_%s_%s_%s.pdf".formatted(
                fullName.replaceAll("[^a-zA-Z0-9]+", "_"), employee.getEmployeeId(), java.time.LocalDate.now());
        String fileUrl = fileStorageService.storeBytes(pdf, storageFileName,
                FileModule.EMPLOYEE, employee.getEmployeeId(), "offer-letters");
        var document = documentService.uploadDocument(new DocumentUploadRequest(
                employee.getId(), "Offer Letter", Document.DocumentType.OFFER_LETTER,
                fileUrl, downloadFileName, pdf.length, "application/pdf",
                "Regenerated on request"));

        auditService.record("Offer Letter Generated", employee.getEmployeeId(), fullName);
        // Relative path via our own download proxy (see DocumentService.downloadDocument) -
        // not a raw Cloudinary URL, so no UUID and a real filename reach the browser.
        return document.fileUrl();
    }

    // Uses the reusable Document Studio engine (DocumentGenerationService) rather than a
    // hand-written PDF builder like generateOfferLetter's - this is the pattern any future
    // document type should follow: build a placeholder map, call generateAndStore().
    public String generateExperienceLetter(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        String fullName = employee.getFirstName() + " " + employee.getLastName();
        String designationTitle = employee.getDesignation() != null ? employee.getDesignation().getTitle() : "";
        String departmentName = employee.getDepartment() != null ? employee.getDepartment().getName() : "";

        Map<String, String> fields = Map.of(
                "EmployeeName", fullName,
                "EmployeeID", employee.getEmployeeId(),
                "Designation", designationTitle,
                "Department", departmentName,
                "JoiningDate", employee.getDateOfJoining() != null ? employee.getDateOfJoining().toString() : "",
                "LastWorkingDate", java.time.LocalDate.now().toString()
        );

        String fileUrl = documentGenerationService.generateAndStore(
                Document.DocumentType.EXPERIENCE_LETTER, fields, employee, "Experience Letter");
        auditService.record("Experience Letter Generated", employee.getEmployeeId(), fullName);
        return fileUrl;
    }

    public String generateRelievingLetter(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        String fullName = employee.getFirstName() + " " + employee.getLastName();
        String designationTitle = employee.getDesignation() != null ? employee.getDesignation().getTitle() : "";

        Map<String, String> fields = Map.of(
                "EmployeeName", fullName,
                "EmployeeID", employee.getEmployeeId(),
                "Designation", designationTitle,
                "LastWorkingDate", java.time.LocalDate.now().toString()
        );

        String fileUrl = documentGenerationService.generateAndStore(
                Document.DocumentType.RELIEVING_LETTER, fields, employee, "Relieving Letter");
        auditService.record("Relieving Letter Generated", employee.getEmployeeId(), fullName);
        return fileUrl;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public List<ManagerOptionResponse> getManagerOptions() {
        return employeeRepository.findAllManagers().stream()
                .map(e -> new ManagerOptionResponse(
                        e.getId(),
                        e.getFirstName() + " " + e.getLastName(),
                        e.getDesignation() != null ? e.getDesignation().getTitle() : null))
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
        employee.setCustomAllowance(breakup.get("customAllowance"));
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

        auditService.record("Salary Updated", employee.getEmployeeId(),
                employee.getFirstName() + " " + employee.getLastName() + ": " + oldCtc + " -> " + request.newAnnualCtc());

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
        employee.setLifecycleStatus(Employee.LifecycleStatus.NOTICE_PERIOD);
        employee = employeeRepository.save(employee);

        emailService.sendResignationAcknowledgementEmail(
                employee.getEmail(),
                employee.getFirstName() + " " + employee.getLastName(),
                request.lastWorkingDate()
        );

        // Also kicks off the full offboarding workflow (checklist + stage pipeline) - this old
        // endpoint's contract (EmployeeResponse) is left untouched so the existing frontend call
        // from EmployeeDirectory.jsx keeps working exactly as before.
        try {
            offboardingService.initiateOffboarding(id,
                    new InitiateOffboardingRequest(OffboardingCase.Type.RESIGNATION, request.lastWorkingDate(), request.reason(), false),
                    null);
        } catch (Exception e) {
            log.warn("Could not initiate offboarding case for employee {}: {}", employee.getEmployeeId(), e.getMessage());
        }

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
        RoleEnum oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        auditService.record("Role Changed", employee.getEmployeeId(),
                employee.getFirstName() + " " + employee.getLastName() + ": " + oldRole + " -> " + newRole);
        return toResponse(employee);
    }

    /** Updates the onboarding checklist flags (documents, assets, bank details, induction). */
    @Transactional
    public EmployeeResponse updateOnboardingChecklist(UUID id, OnboardingChecklistRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (request.documentsVerified() != null) employee.setOnboardingDocumentsVerified(request.documentsVerified());
        if (request.assetsAssigned() != null) employee.setOnboardingAssetsAssigned(request.assetsAssigned());
        if (request.bankDetailsCollected() != null) employee.setOnboardingBankDetailsCollected(request.bankDetailsCollected());
        if (request.inductionCompleted() != null) employee.setOnboardingInductionCompleted(request.inductionCompleted());
        // Induction completion is the signal that onboarding is done - advance the lifecycle from
        // wherever it was pre-join (PRE_BOARDING/JOINING_TODAY) to ACTIVE. Only move forward, never
        // backward, and never touch it if HR has already advanced it further (e.g. PROBATION+).
        if (Boolean.TRUE.equals(request.inductionCompleted())
                && (employee.getLifecycleStatus() == Employee.LifecycleStatus.PRE_BOARDING
                    || employee.getLifecycleStatus() == Employee.LifecycleStatus.JOINING_TODAY)) {
            employee.setLifecycleStatus(Employee.LifecycleStatus.ACTIVE);
        }
        employee = employeeRepository.save(employee);
        return toResponse(employee);
    }

    private static final java.util.Set<RoleEnum> LIFECYCLE_OVERRIDE_ROLES =
            java.util.Set.of(RoleEnum.CEO, RoleEnum.HR_MANAGER, RoleEnum.ADMIN);

    /** Manual HR override for lifecycle status transitions that nothing else automates (e.g. PROBATION -> CONFIRMED). */
    @Transactional
    public EmployeeResponse updateLifecycleStatus(UUID id, Employee.LifecycleStatus newStatus) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Employee.LifecycleStatus oldStatus = employee.getLifecycleStatus();
        employee.setLifecycleStatus(newStatus);
        employee = employeeRepository.save(employee);
        auditService.record("Lifecycle Status Changed", employee.getEmployeeId(),
                employee.getFirstName() + " " + employee.getLastName() + ": " + oldStatus + " -> " + newStatus);
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
                employee.getPersonalEmail(),
                employee.getPersonalMobile(),
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
                employee.getCustomAllowance(),
                employee.getGrossSalary(),
                employee.getCtc(),
                employee.isActive(),
                employee.getCreatedAt(),
                employee.getUser() != null ? employee.getUser().getRole().name() : null,
                employee.isOnboardingDocumentsVerified(),
                employee.isOnboardingAssetsAssigned(),
                employee.isOnboardingBankDetailsCollected(),
                employee.isOnboardingInductionCompleted(),
                employee.getNomineeName(),
                employee.getNomineeRelation(),
                employee.getNomineeDateOfBirth(),
                employee.getNomineeSharePercentage(),
                employee.getNomineeGender(),
                employee.getMaritalStatus(),
                employee.getNationality(),
                employee.getBloodGroup(),
                employee.getLanguagesKnown(),
                employee.getLifecycleStatus(),
                employee.getCostCenter(),
                employee.getBusinessUnit()
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
