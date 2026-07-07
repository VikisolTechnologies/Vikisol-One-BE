package com.vikisol.one.recruitment.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.common.service.FileModule;
import com.vikisol.one.common.service.FileStorageService;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.doctemplate.service.DocumentGenerationService;
import com.vikisol.one.document.dto.DocumentUploadRequest;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.document.service.DocumentService;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.service.EmployeeService;
import com.vikisol.one.notification.entity.Notification;
import com.vikisol.one.notification.service.NotificationService;
import com.vikisol.one.payroll.service.PayrollService;
import com.vikisol.one.recruitment.dto.*;
import com.vikisol.one.recruitment.entity.Candidate;
import com.vikisol.one.recruitment.entity.CandidateFieldChange;
import com.vikisol.one.recruitment.entity.Interview;
import com.vikisol.one.recruitment.entity.JobPosting;
import com.vikisol.one.recruitment.repository.CandidateFieldChangeRepository;
import com.vikisol.one.recruitment.repository.CandidateRepository;
import com.vikisol.one.recruitment.repository.InterviewRepository;
import com.vikisol.one.recruitment.repository.JobPostingRepository;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RecruitmentService {

    private final JobPostingRepository jobPostingRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final CandidateFieldChangeRepository candidateFieldChangeRepository;
    private final EntityManager entityManager;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final PayrollService payrollService;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;
    private final DocumentService documentService;
    private final DocumentGenerationService documentGenerationService;
    private final com.vikisol.one.settings.service.BrandingService brandingService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final com.vikisol.one.integration.service.IntegrationService integrationService;

    // ─── Job Postings ───

    public JobPostingResponse createJobPosting(JobPostingRequest request, UUID postedById) {
        JobPosting job = JobPosting.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .department(entityManager.getReference(Department.class, request.getDepartmentId()))
                .designation(entityManager.getReference(Designation.class, request.getDesignationId()))
                .location(request.getLocation())
                .employmentType(request.getEmploymentType() != null ? request.getEmploymentType() : JobPosting.EmploymentType.FULL_TIME)
                .experienceMin(request.getExperienceMin())
                .experienceMax(request.getExperienceMax())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .skills(request.getSkills())
                .numberOfPositions(request.getNumberOfPositions())
                .status(request.getStatus() != null ? request.getStatus() : JobPosting.Status.DRAFT)
                .postedById(postedById)
                .postedDate(LocalDate.now())
                .closingDate(request.getClosingDate())
                .build();
        job = jobPostingRepository.save(job);
        notifyRecruitersOfNewJobPosting(job);
        return mapJobPosting(job);
    }

    // Every recruiter should hear about a new opening immediately, not just whoever happens to
    // check the Job Postings dashboard - previously creating a job posting sent no notification
    // to anyone at all.
    private void notifyRecruitersOfNewJobPosting(JobPosting job) {
        List<com.vikisol.one.auth.entity.User> recruiters = userRepository.findByRoleIn(List.of(com.vikisol.one.security.RoleEnum.RECRUITER));
        for (com.vikisol.one.auth.entity.User recruiter : recruiters) {
            notificationService.sendNotification(
                    recruiter.getId(),
                    "New Job Posting: " + job.getTitle(),
                    "A new opening for " + job.getTitle() + " (" + job.getNumberOfPositions() + " position(s)) has been posted.",
                    Notification.NotificationType.RECRUITMENT, job.getId(), "JOB_POSTING",
                    Notification.Priority.MEDIUM, "recruitment", "/job-postings"
            );
        }
    }

    public JobPostingResponse updateJobPosting(UUID id, JobPostingRequest request) {
        JobPosting job = jobPostingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job posting not found"));
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setDepartment(entityManager.getReference(Department.class, request.getDepartmentId()));
        job.setDesignation(entityManager.getReference(Designation.class, request.getDesignationId()));
        job.setLocation(request.getLocation());
        if (request.getEmploymentType() != null) job.setEmploymentType(request.getEmploymentType());
        job.setExperienceMin(request.getExperienceMin());
        job.setExperienceMax(request.getExperienceMax());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setSkills(request.getSkills());
        job.setNumberOfPositions(request.getNumberOfPositions());
        if (request.getStatus() != null) job.setStatus(request.getStatus());
        job.setClosingDate(request.getClosingDate());
        return mapJobPosting(jobPostingRepository.save(job));
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJobPosting(UUID id) {
        return mapJobPosting(jobPostingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job posting not found")));
    }

    @Transactional(readOnly = true)
    public Page<JobPostingResponse> getActiveJobPostings(Pageable pageable) {
        return jobPostingRepository.findByIsActiveTrue(pageable).map(this::mapJobPosting);
    }

    public void deleteJobPosting(UUID id) {
        JobPosting job = jobPostingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job posting not found"));
        job.setActive(false);
        jobPostingRepository.save(job);
    }

    // ─── Candidates ───

    private String generateNextCandidateCode() {
        List<Candidate> all = candidateRepository.findAll();
        int maxNum = all.stream()
                .map(Candidate::getCandidateCode)
                .filter(code -> code != null && code.startsWith("CAN-"))
                .map(code -> code.substring(4))
                .mapToInt(num -> {
                    try { return Integer.parseInt(num); } catch (NumberFormatException e) { return 0; }
                })
                .max()
                .orElse(0);
        return String.format("CAN-%04d", maxNum + 1);
    }

    public CandidateResponse createCandidate(CandidateRequest request, UserPrincipal principal) {
        Employee creator = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;
        Candidate candidate = Candidate.builder()
                .candidateCode(generateNextCandidateCode())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .alternateMobile(request.getAlternateMobile())
                .currentAddress(request.getCurrentAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .linkedinUrl(request.getLinkedinUrl())
                .githubUrl(request.getGithubUrl())
                .portfolioUrl(request.getPortfolioUrl())
                .currentCompany(request.getCurrentCompany())
                .currentDesignation(request.getCurrentDesignation())
                .employmentType(request.getEmploymentType())
                .experienceYears(request.getExperienceYears())
                .relevantExperienceYears(request.getRelevantExperienceYears())
                .certifications(request.getCertifications())
                .expectedSalary(request.getExpectedSalary())
                .currentCtc(request.getCurrentCtc())
                .noticePeriod(request.getNoticePeriod())
                .currentLocation(request.getCurrentLocation())
                .preferredLocation(request.getPreferredLocation())
                .resumeUrl(request.getResumeUrl())
                .skills(request.getSkills())
                .source(request.getSource() != null ? request.getSource() : Candidate.Source.DIRECT)
                .notes(request.getNotes())
                .assignedRecruiterId(request.getAssignedRecruiterId() != null ? request.getAssignedRecruiterId() : (creator != null ? creator.getId() : null))
                .hiringManagerId(request.getHiringManagerId())
                .businessUnit(request.getBusinessUnit())
                .priority(request.getPriority() != null ? request.getPriority() : Candidate.Priority.MEDIUM)
                .build();
        if (request.getJobPostingId() != null) {
            candidate.setJobPosting(entityManager.getReference(JobPosting.class, request.getJobPostingId()));
        }
        return mapCandidate(candidateRepository.save(candidate));
    }

    // Full-profile edit - covers every field an ATS should let a recruiter/HR update (personal,
    // professional, recruitment-ownership). Every field that actually changes gets a
    // CandidateFieldChange row (same audit mechanism as updateCandidateFields), so nothing here
    // silently overwrites recruitment history - satisfies "every editable field maintains history".
    public CandidateResponse updateCandidate(UUID id, CandidateRequest request, UserPrincipal principal) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        Employee modifier = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;
        String modifierName = modifier != null ? modifier.getFirstName() + " " + modifier.getLastName() : "System";

        recordFieldChange(candidate, "Full Name", candidate.getFirstName() + " " + candidate.getLastName(), request.getFirstName() + " " + request.getLastName(), modifier, modifierName);
        recordFieldChange(candidate, "Personal Email", candidate.getEmail(), request.getEmail(), modifier, modifierName);
        recordFieldChange(candidate, "Personal Mobile", candidate.getPhone(), request.getPhone(), modifier, modifierName);
        recordFieldChange(candidate, "Current Company", candidate.getCurrentCompany(), request.getCurrentCompany(), modifier, modifierName);
        recordFieldChange(candidate, "Current Designation", candidate.getCurrentDesignation(), request.getCurrentDesignation(), modifier, modifierName);
        recordFieldChange(candidate, "Assigned Recruiter", candidate.getAssignedRecruiterId(), request.getAssignedRecruiterId(), modifier, modifierName);
        recordFieldChange(candidate, "Hiring Manager", candidate.getHiringManagerId(), request.getHiringManagerId(), modifier, modifierName);
        recordFieldChange(candidate, "Priority", candidate.getPriority(), request.getPriority(), modifier, modifierName);
        recordFieldChange(candidate, "Status/Remarks", candidate.getNotes(), request.getNotes(), modifier, modifierName);

        candidate.setFirstName(request.getFirstName());
        candidate.setLastName(request.getLastName());
        candidate.setEmail(request.getEmail());
        candidate.setPhone(request.getPhone());
        candidate.setAlternateMobile(request.getAlternateMobile());
        candidate.setCurrentAddress(request.getCurrentAddress());
        candidate.setCity(request.getCity());
        candidate.setState(request.getState());
        candidate.setCountry(request.getCountry());
        candidate.setLinkedinUrl(request.getLinkedinUrl());
        candidate.setGithubUrl(request.getGithubUrl());
        candidate.setPortfolioUrl(request.getPortfolioUrl());
        candidate.setCurrentCompany(request.getCurrentCompany());
        candidate.setCurrentDesignation(request.getCurrentDesignation());
        candidate.setEmploymentType(request.getEmploymentType());
        candidate.setExperienceYears(request.getExperienceYears());
        candidate.setRelevantExperienceYears(request.getRelevantExperienceYears());
        candidate.setCertifications(request.getCertifications());
        candidate.setExpectedSalary(request.getExpectedSalary());
        candidate.setCurrentCtc(request.getCurrentCtc());
        candidate.setNoticePeriod(request.getNoticePeriod());
        candidate.setCurrentLocation(request.getCurrentLocation());
        candidate.setPreferredLocation(request.getPreferredLocation());
        candidate.setResumeUrl(request.getResumeUrl());
        candidate.setSkills(request.getSkills());
        if (request.getSource() != null) candidate.setSource(request.getSource());
        candidate.setNotes(request.getNotes());
        // A recruiter (even the one currently assigned) can never reassign a candidate to someone
        // else - only HR Manager/CEO/Admin can move a candidate from one recruiter to another.
        // Previously any recruiter could silently overwrite assignedRecruiterId through this same
        // full-profile update with no ownership check at all.
        if (request.getAssignedRecruiterId() != null && !request.getAssignedRecruiterId().equals(candidate.getAssignedRecruiterId())) {
            boolean canReassign = principal != null && principal.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
            if (!canReassign) {
                throw new com.vikisol.one.common.exception.BadRequestException("Only HR Manager, CEO, or Admin can reassign a candidate to a different recruiter");
            }
            candidate.setAssignedRecruiterId(request.getAssignedRecruiterId());
        }
        candidate.setHiringManagerId(request.getHiringManagerId());
        candidate.setBusinessUnit(request.getBusinessUnit());
        if (request.getPriority() != null) candidate.setPriority(request.getPriority());
        if (request.getJobPostingId() != null) {
            candidate.setJobPosting(entityManager.getReference(JobPosting.class, request.getJobPostingId()));
        }
        return mapCandidate(candidateRepository.save(candidate));
    }

    // Self-claim: only succeeds if the candidate has no recruiter yet (direct/Arena applicants).
    // Never lets a recruiter take a candidate away from someone else - that requires HR/CEO/Admin
    // via updateCandidate's reassignment guard instead.
    public CandidateResponse selfAssignCandidate(UUID id, UserPrincipal principal) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        if (candidate.getAssignedRecruiterId() != null) {
            throw new com.vikisol.one.common.exception.BadRequestException(
                    "This candidate already has an assigned recruiter - ask HR Manager or CEO to reassign");
        }
        Employee self = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found for current user"));
        candidate.setAssignedRecruiterId(self.getId());
        auditService.record("Candidate Self-Assigned", candidate.getFirstName() + " " + candidate.getLastName(),
                "Assigned to " + self.getFirstName() + " " + self.getLastName());
        return mapCandidate(candidateRepository.save(candidate));
    }

    /**
     * HR reviewing a recruiter's offer proposal can adjust it (CTC, joining bonus, variable pay,
     * joining date, designation, department, reporting manager) before approving - the approval
     * popup should not be read-only. Does not change status or notify anyone by itself; the HR
     * user still calls approve-selection (or request-revision) afterward.
     */
    public CandidateResponse updateOfferProposal(UUID id, SelectCandidateRequest request, UserPrincipal principal) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        if (candidate.getStatus() != Candidate.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("This candidate has no pending offer proposal to adjust");
        }
        Employee modifier = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;
        String modifierName = modifier != null ? modifier.getFirstName() + " " + modifier.getLastName() : "System";

        recordFieldChange(candidate, "Proposed CTC", candidate.getOfferedCtc(), request.offeredCtc(), modifier, modifierName);
        recordFieldChange(candidate, "Joining Bonus", candidate.getOfferedJoiningBonus(), request.joiningBonus(), modifier, modifierName);
        recordFieldChange(candidate, "Variable Pay", candidate.getOfferedVariablePay(), request.variablePay(), modifier, modifierName);
        recordFieldChange(candidate, "Joining Date", candidate.getOfferedDateOfJoining(), request.dateOfJoining(), modifier, modifierName);

        if (request.offeredCtc() != null) candidate.setOfferedCtc(request.offeredCtc());
        if (request.joiningBonus() != null) candidate.setOfferedJoiningBonus(request.joiningBonus());
        if (request.variablePay() != null) candidate.setOfferedVariablePay(request.variablePay());
        if (request.dateOfJoining() != null) candidate.setOfferedDateOfJoining(request.dateOfJoining());
        if (request.reportingManagerId() != null) candidate.setOfferedReportingManagerId(request.reportingManagerId());
        if (request.designationId() != null) candidate.setOfferedDesignation(entityManager.getReference(Designation.class, request.designationId()));
        if (request.departmentId() != null) candidate.setOfferedDepartment(entityManager.getReference(Department.class, request.departmentId()));

        return mapCandidate(candidateRepository.save(candidate));
    }

    /**
     * Dedicated endpoint for the 5 fields that need field-level audit history (Expected/Current
     * CTC, Notice Period, Current/Preferred Location) - only touches a field if the request
     * actually supplies a new value, and records a CandidateFieldChange row per field that
     * actually changed (skips no-op writes of the same value).
     */
    public CandidateResponse updateCandidateFields(UUID id, CandidateFieldsUpdateRequest request, UserPrincipal principal) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        Employee modifier = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;
        String modifierName = modifier != null ? modifier.getFirstName() + " " + modifier.getLastName() : "System";

        recordFieldChange(candidate, "Expected CTC", candidate.getExpectedSalary(), request.expectedSalary(), modifier, modifierName);
        recordFieldChange(candidate, "Current CTC", candidate.getCurrentCtc(), request.currentCtc(), modifier, modifierName);
        recordFieldChange(candidate, "Notice Period", candidate.getNoticePeriod(), request.noticePeriod(), modifier, modifierName);
        recordFieldChange(candidate, "Current Location", candidate.getCurrentLocation(), request.currentLocation(), modifier, modifierName);
        recordFieldChange(candidate, "Preferred Location", candidate.getPreferredLocation(), request.preferredLocation(), modifier, modifierName);

        if (request.expectedSalary() != null) candidate.setExpectedSalary(request.expectedSalary());
        if (request.currentCtc() != null) candidate.setCurrentCtc(request.currentCtc());
        if (request.noticePeriod() != null) candidate.setNoticePeriod(request.noticePeriod());
        if (request.currentLocation() != null) candidate.setCurrentLocation(request.currentLocation());
        if (request.preferredLocation() != null) candidate.setPreferredLocation(request.preferredLocation());

        return mapCandidate(candidateRepository.save(candidate));
    }

    private void recordFieldChange(Candidate candidate, String fieldName, Object oldValue, Object newValue, Employee modifier, String modifierName) {
        if (newValue == null || Objects.equals(String.valueOf(oldValue), String.valueOf(newValue))) return;
        auditService.record(fieldName + " Changed", candidate.getFirstName() + " " + candidate.getLastName(),
                oldValue + " -> " + newValue);
        candidateFieldChangeRepository.save(CandidateFieldChange.builder()
                .candidate(candidate)
                .fieldName(fieldName)
                .previousValue(String.valueOf(oldValue))
                .newValue(String.valueOf(newValue))
                .modifiedById(modifier != null ? modifier.getId() : null)
                .modifiedByName(modifierName)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CandidateFieldChangeResponse> getCandidateFieldHistory(UUID candidateId) {
        return candidateFieldChangeRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
                .map(c -> new CandidateFieldChangeResponse(c.getId(), c.getFieldName(), c.getPreviousValue(),
                        c.getNewValue(), c.getModifiedByName(), c.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateResponse getCandidate(UUID id) {
        return mapCandidate(candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found")));
    }

    @Transactional(readOnly = true)
    public Page<CandidateResponse> getCandidates(Pageable pageable) {
        return candidateRepository.findAll(pageable).map(this::mapCandidate);
    }

    // Statuses that are only reachable via the propose/approve/revision endpoints (which enforce the
    // recruiter-proposes/manager-approves workflow and side effects like emailing the offer). Blocking
    // them here prevents a recruiter from using this generic endpoint to bypass manager approval.
    private static final java.util.Set<Candidate.Status> APPROVAL_GATED_STATUSES = java.util.Set.of(
            Candidate.Status.PENDING_APPROVAL, Candidate.Status.REVISION_REQUESTED,
            Candidate.Status.SELECTED, Candidate.Status.OFFER_MADE,
            Candidate.Status.OFFER_ACCEPTED, Candidate.Status.JOINED);

    public CandidateResponse updateCandidateStatus(UUID id, Candidate.Status status, boolean isRecruiter) {
        if (isRecruiter && APPROVAL_GATED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Recruiters cannot set this status directly - use the propose/approve workflow");
        }
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        Candidate.Status previousStatus = candidate.getStatus();
        candidate.setStatus(status);
        candidate = candidateRepository.save(candidate);
        if (status != previousStatus) {
            notifyStageChange(candidate, previousStatus, status);
        }
        return mapCandidate(candidate);
    }

    // Every stage move (e.g. Screening -> Technical/L1) notifies HR Manager, CEO, and the
    // candidate's own assigned recruiter - previously a stage change notified no one at all.
    private void notifyStageChange(Candidate candidate, Candidate.Status from, Candidate.Status to) {
        String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
        String title = candidateName + " moved to " + to;
        String message = candidateName + "'s stage changed from " + from + " to " + to + ".";

        userRepository.findByRoleIn(List.of(com.vikisol.one.security.RoleEnum.CEO, com.vikisol.one.security.RoleEnum.HR_MANAGER))
                .forEach(u -> notificationService.sendNotification(u.getId(), title, message,
                        Notification.NotificationType.RECRUITMENT, candidate.getId(), "CANDIDATE",
                        Notification.Priority.LOW, "recruitment", "/recruitment"));

        if (candidate.getAssignedRecruiterId() != null) {
            employeeRepository.findById(candidate.getAssignedRecruiterId())
                    .map(Employee::getUser)
                    .ifPresent(recruiterUser -> notificationService.sendNotification(recruiterUser.getId(), title, message,
                            Notification.NotificationType.RECRUITMENT, candidate.getId(), "CANDIDATE",
                            Notification.Priority.LOW, "recruitment", "/recruitment"));
        }
    }

    public void deleteCandidate(UUID id) {
        candidateRepository.deleteById(id);
    }

    /**
     * A recruiter proposes CTC/designation/department/joining date for a candidate. This does NOT
     * create an employee or send an offer letter - recruiters can only propose, a manager has to
     * approve before anything goes out. Re-calling this (e.g. after a REVISION_REQUESTED) resets
     * the proposal and clears any manager remarks.
     */
    public CandidateResponse proposeSelection(UUID id, SelectCandidateRequest request, UserPrincipal principal) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));

        Designation designation = entityManager.getReference(Designation.class, request.designationId());
        Department department = entityManager.getReference(Department.class, request.departmentId());
        Employee proposer = employeeRepository.findByUserId(principal.getId()).orElse(null);

        candidate.setStatus(Candidate.Status.PENDING_APPROVAL);
        candidate.setOfferedCtc(request.offeredCtc());
        candidate.setOfferedDesignation(designation);
        candidate.setOfferedDepartment(department);
        candidate.setOfferedDateOfJoining(request.dateOfJoining());
        candidate.setOfferedReportingManagerId(request.reportingManagerId());
        candidate.setManagerRemarks(null);
        if (proposer != null) candidate.setProposedById(proposer.getId());
        candidateRepository.save(candidate);

        String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
        auditService.record("Offer Proposal Submitted", candidateName, "CTC " + request.offeredCtc());
        for (var hr : userRepository.findByRoleIn(List.of(com.vikisol.one.security.RoleEnum.CEO, com.vikisol.one.security.RoleEnum.HR_MANAGER))) {
            notificationService.sendNotification(hr.getId(), "Approval Pending",
                    "A new offer proposal for " + candidateName + " (CTC " + request.offeredCtc() + ") is awaiting your approval.",
                    Notification.NotificationType.RECRUITMENT, candidate.getId(), "CANDIDATE");
        }

        return mapCandidate(candidate);
    }

    /**
     * Manager approval: generates the employee record + employee ID using the CEO-defined standard
     * CTC breakup, and emails the offer/congratulations letter. Only valid on a candidate the
     * recruiter has already proposed terms for.
     */
    public SelectCandidateResponse approveSelection(UUID id) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        if (candidate.getStatus() != Candidate.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("This candidate has no pending offer proposal to approve");
        }
        if (candidate.getOfferedCtc() == null || candidate.getOfferedDesignation() == null || candidate.getOfferedDepartment() == null) {
            throw new IllegalStateException("The offer proposal is incomplete");
        }

        Map<String, BigDecimal> breakup = payrollService.computeCtcBreakup(candidate.getOfferedCtc());

        EmployeeRequest employeeRequest = new EmployeeRequest(
                null,
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getEmail(),
                candidate.getPhone(),
                // The candidate's recruitment-stage email/phone are their personal contact details -
                // preserved here so post-hire activation/joining communication still reaches them
                // even after `email`/`phone` above later get repointed to official company contacts.
                candidate.getEmail(),
                candidate.getPhone(),
                null,
                null,
                candidate.getOfferedDepartment().getId(),
                candidate.getOfferedDesignation().getId(),
                candidate.getOfferedDateOfJoining(),
                null, null,
                candidate.getOfferedReportingManagerId(),
                Employee.EmploymentType.FULL_TIME,
                Employee.EmploymentStatus.ACTIVE,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                breakup.get("basicSalary"),
                breakup.get("hra"),
                breakup.get("conveyanceAllowance"),
                breakup.get("medicalAllowance"),
                breakup.get("specialAllowance"),
                breakup.get("customAllowance"),
                breakup.get("grossSalary"),
                breakup.get("ctc"),
                null, null, null, null, null, null, null, null, null
        );
        EmployeeResponse employee = employeeService.createEmployee(employeeRequest);

        candidate.setStatus(Candidate.Status.SELECTED);
        candidate.setConvertedEmployeeId(employee.employeeId());
        candidate.setManagerRemarks(null);
        candidateRepository.save(candidate);

        Employee reportingManagerEmployee = candidate.getOfferedReportingManagerId() != null
                ? employeeRepository.findById(candidate.getOfferedReportingManagerId()).orElse(null)
                : null;
        String reportingManagerName = reportingManagerEmployee != null
                ? reportingManagerEmployee.getFirstName() + " " + reportingManagerEmployee.getLastName()
                : null;

        String candidateFullName = candidate.getFirstName() + " " + candidate.getLastName();
        byte[] offerLetterPdf = null;
        try {
            offerLetterPdf = documentGenerationService.render(Document.DocumentType.OFFER_LETTER,
                    offerLetterFields(candidate, candidateFullName, employee, breakup, reportingManagerEmployee, reportingManagerName));

            String fileName = "Offer_Letter_" + employee.employeeId() + ".pdf";
            String fileUrl = fileStorageService.storeBytes(offerLetterPdf, fileName,
                    FileModule.EMPLOYEE, employee.employeeId(), "offer-letters");
            documentService.uploadDocument(new DocumentUploadRequest(
                    employee.id(), "Offer Letter", Document.DocumentType.OFFER_LETTER,
                    fileUrl, fileName, offerLetterPdf.length, "application/pdf",
                    "Auto-generated on manager approval"));
        } catch (Exception e) {
            // Don't block the approval/employee-creation flow if PDF generation fails - the
            // notification email still goes out, just without the attachment this one time.
            log.warn("Could not generate/store offer letter PDF for candidate {}: {}", candidate.getId(), e.getMessage());
        }

        emailService.sendOfferLetterEmail(
                candidate.getEmail(),
                candidateFullName,
                employee.employeeId(),
                employee.designationTitle(),
                candidate.getOfferedCtc(),
                breakup,
                candidate.getOfferedDateOfJoining(),
                reportingManagerName,
                offerLetterPdf
        );

        auditService.record("Offer Approved", employee.employeeId(),
                candidateFullName + " (" + candidate.getEmail() + "), CTC " + candidate.getOfferedCtc());
        auditService.record("Offer Generated", employee.employeeId(),
                candidateFullName + " - " + employee.designationTitle() + ", CTC " + candidate.getOfferedCtc());

        if (candidate.getProposedById() != null) {
            employeeRepository.findById(candidate.getProposedById()).ifPresent(recruiter -> {
                if (recruiter.getUser() != null) {
                    notificationService.sendNotification(recruiter.getUser().getId(), "Approval Completed",
                            "Your offer proposal for " + candidateFullName + " has been approved and the offer letter sent.",
                            Notification.NotificationType.RECRUITMENT, candidate.getId(), "CANDIDATE");
                }
            });
        }

        return new SelectCandidateResponse(
                candidate.getId(),
                employee.employeeId(),
                candidate.getFirstName() + " " + candidate.getLastName(),
                candidate.getEmail(),
                breakup,
                true
        );
    }

    // Builds the {{Placeholder}} field map for the Corporate Offer Letter template (see
    // DataSeeder.seedOfferLetterTemplate) from real candidate/offer data. Delegates the actual
    // mapping to OfferLetterFieldsHelper, shared with EmployeeService.generateOfferLetter() so
    // the two callers can't drift apart on field names, and so company-wide values (office
    // location, work hours, orientation contact) come from Company Branding settings instead of
    // being hardcoded per-caller. Candidate has no gender field on file, so Salutation cannot be
    // reliably derived here and keeps the "Mr./Ms." default.
    private Map<String, String> offerLetterFields(Candidate candidate, String candidateFullName, EmployeeResponse employee,
                                                    Map<String, BigDecimal> breakup, Employee reportingManagerEmployee,
                                                    String reportingManagerName) {
        String reportingManagerTitle = reportingManagerEmployee != null && reportingManagerEmployee.getDesignation() != null
                ? reportingManagerEmployee.getDesignation().getTitle() : null;
        // computeCtcBreakup's "ctc" entry is the net-of-deductions annual figure (the annualCtc
        // argument it was called with) - matches Annexure B's "Total CTC (A+B)" annual column.
        BigDecimal totalCtcAnnual = breakup.getOrDefault("ctc", BigDecimal.ZERO);
        BigDecimal pt = payrollService.getConfigAsBigDecimal("PROFESSIONAL_TAX");
        return com.vikisol.one.doctemplate.service.OfferLetterFieldsHelper.build(
                candidateFullName, employee.designationTitle(), null,
                candidate.getOfferedDateOfJoining(), reportingManagerTitle, reportingManagerName,
                breakup, totalCtcAnnual, pt, brandingService.getBranding());
    }

    /**
     * Manager sends a proposal back to the recruiter with remarks (e.g. CTC too high). Notifies
     * the recruiter in-app and by email so they know to revise and resubmit.
     */
    public CandidateResponse requestRevision(UUID id, String remarks) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        if (candidate.getStatus() != Candidate.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("This candidate has no pending offer proposal to send back");
        }
        candidate.setStatus(Candidate.Status.REVISION_REQUESTED);
        candidate.setManagerRemarks(remarks);
        candidateRepository.save(candidate);

        if (candidate.getProposedById() != null) {
            employeeRepository.findById(candidate.getProposedById()).ifPresent(recruiter -> {
                String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
                if (recruiter.getUser() != null) {
                    notificationService.sendNotification(
                            recruiter.getUser().getId(),
                            "Offer Proposal Needs Changes",
                            "Your offer proposal for " + candidateName + " was sent back: " + remarks,
                            Notification.NotificationType.RECRUITMENT,
                            candidate.getId(),
                            "CANDIDATE"
                    );
                }
                if (recruiter.getEmail() != null) {
                    emailService.sendEmail(
                            recruiter.getEmail(),
                            "Offer Proposal Needs Changes - " + candidateName,
                            "The manager reviewed your offer proposal for " + candidateName + " and sent it back with these remarks:\n\n"
                                    + remarks + "\n\nPlease revise the CTC/designation/department and resubmit for approval.\n\nRegards,\nVikisol One"
                    );
                }
            });
        }

        return mapCandidate(candidate);
    }

    // ─── Interviews ───

    public InterviewResponse scheduleInterview(InterviewRequest request, UserPrincipal principal) {
        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        JobPosting jobPosting = jobPostingRepository.findById(request.getJobPostingId())
                .orElseThrow(() -> new EntityNotFoundException("Job posting not found"));
        Employee scheduler = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;

        Interview interview = Interview.builder()
                .candidate(candidate)
                .jobPosting(jobPosting)
                .title(request.getTitle())
                .type(request.getType() != null ? request.getType() : Interview.InterviewType.CUSTOM)
                .interviewerId(request.getInterviewerId())
                .interviewerName(request.getInterviewerName())
                .additionalInterviewerIds(request.getAdditionalInterviewerIds() != null ? request.getAdditionalInterviewerIds() : new ArrayList<>())
                .recruiterId(request.getRecruiterId() != null ? request.getRecruiterId() : (scheduler != null ? scheduler.getId() : null))
                .hrManagerId(request.getHrManagerId())
                .round(request.getRound())
                .orderIndex(request.getOrderIndex())
                .scheduledDate(request.getScheduledDate())
                .scheduledTime(request.getScheduledTime())
                .duration(request.getDuration())
                .timezone(request.getTimezone())
                .mode(request.getMode() != null ? request.getMode() : Interview.Mode.VIDEO)
                .platform(request.getPlatform())
                .meetingLink(request.getMeetingLink())
                .location(request.getLocation())
                .notes(request.getNotes())
                .agenda(request.getAgenda())
                .prepNotes(request.getPrepNotes())
                .attachmentUrls(request.getAttachmentUrls())
                .build();
        interview = interviewRepository.save(interview);

        candidate.setStatus(Candidate.Status.INTERVIEW_SCHEDULED);
        candidateRepository.save(candidate);

        sendInterviewInviteAndNotify(interview, candidate, jobPosting, scheduler, false);

        auditService.record("Interview Scheduled", candidate.getFirstName() + " " + candidate.getLastName(),
                (interview.getTitle() != null ? interview.getTitle() : interview.getType()) + " on " + interview.getScheduledDate() + " " + interview.getScheduledTime());

        return mapInterview(interview);
    }

    /**
     * Full interview edit (title/type/round/duration/timezone/platform/meeting link/location/
     * notes/agenda/prep notes/interviewer/additional interviewers/HR manager) - everything stays
     * editable until the interview is COMPLETED, per the requirement. Diffs interviewer and
     * meeting link specifically since those need their own named audit events (previously the
     * only way to change either was a full reschedule, which didn't log either as its own event).
     */
    public InterviewResponse editInterview(UUID id, InterviewRequest request, UserPrincipal principal) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found"));
        if (interview.getStatus() == Interview.Status.COMPLETED) {
            throw new IllegalStateException("This interview is already completed and can no longer be edited");
        }
        Employee editor = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;
        String editorName = editor != null ? editor.getFirstName() + " " + editor.getLastName() : "System";
        String candidateName = interview.getCandidate().getFirstName() + " " + interview.getCandidate().getLastName();

        if (request.getInterviewerId() != null && !request.getInterviewerId().equals(interview.getInterviewerId())) {
            auditService.record("Interviewer Changed", candidateName,
                    (interview.getInterviewerName() != null ? interview.getInterviewerName() : "-") + " -> " + request.getInterviewerName());
        }
        if (request.getMeetingLink() != null && !request.getMeetingLink().equals(interview.getMeetingLink())) {
            auditService.record("Meeting Link Updated", candidateName, "Round " + interview.getRound());
        }

        interview.setTitle(request.getTitle());
        if (request.getType() != null) interview.setType(request.getType());
        interview.setInterviewerId(request.getInterviewerId());
        interview.setInterviewerName(request.getInterviewerName());
        if (request.getAdditionalInterviewerIds() != null) interview.setAdditionalInterviewerIds(request.getAdditionalInterviewerIds());
        if (request.getHrManagerId() != null) interview.setHrManagerId(request.getHrManagerId());
        if (request.getRound() > 0) interview.setRound(request.getRound());
        interview.setDuration(request.getDuration());
        interview.setTimezone(request.getTimezone());
        if (request.getMode() != null) interview.setMode(request.getMode());
        interview.setPlatform(request.getPlatform());
        interview.setMeetingLink(request.getMeetingLink());
        interview.setLocation(request.getLocation());
        interview.setNotes(request.getNotes());
        interview.setAgenda(request.getAgenda());
        interview.setPrepNotes(request.getPrepNotes());
        interview.setAttachmentUrls(request.getAttachmentUrls());
        interview = interviewRepository.save(interview);

        auditService.record("Interview Edited", candidateName, "Round " + interview.getRound() + " updated by " + editorName);
        return mapInterview(interview);
    }

    /** Recruiter reorders a candidate's interview rounds (drag-and-drop in the UI) - just persists the new orderIndex per interview id, in the order given. */
    public List<InterviewResponse> reorderInterviews(UUID candidateId, List<UUID> orderedInterviewIds) {
        List<Interview> interviews = interviewRepository.findByCandidateId(candidateId);
        Map<UUID, Interview> byId = interviews.stream().collect(java.util.stream.Collectors.toMap(Interview::getId, i -> i));
        for (int index = 0; index < orderedInterviewIds.size(); index++) {
            Interview interview = byId.get(orderedInterviewIds.get(index));
            if (interview != null) {
                interview.setOrderIndex(index);
                interviewRepository.save(interview);
            }
        }
        return getCandidateInterviews(candidateId);
    }

    public InterviewResponse rescheduleInterview(UUID id, RescheduleInterviewRequest request, UserPrincipal principal) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found"));
        Candidate candidate = interview.getCandidate();
        JobPosting jobPosting = interview.getJobPosting();
        Employee scheduler = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;

        LocalDate oldDate = interview.getScheduledDate();
        java.time.LocalTime oldTime = interview.getScheduledTime();

        interview.setScheduledDate(request.scheduledDate());
        interview.setScheduledTime(request.scheduledTime());
        interview.setStatus(Interview.Status.SCHEDULED);
        interview = interviewRepository.save(interview);

        sendInterviewInviteAndNotify(interview, candidate, jobPosting, scheduler, true);

        auditService.record("Interview Rescheduled", candidate.getFirstName() + " " + candidate.getLastName(),
                oldDate + " " + oldTime + " -> " + interview.getScheduledDate() + " " + interview.getScheduledTime()
                        + (request.reason() != null ? " (" + request.reason() + ")" : ""));

        return mapInterview(interview);
    }

    public InterviewResponse cancelInterview(UUID id, CancelInterviewRequest request, UserPrincipal principal) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found"));
        Candidate candidate = interview.getCandidate();

        interview.setStatus(Interview.Status.CANCELLED);
        interview.setCancellationReason(request.reason());
        interview = interviewRepository.save(interview);

        String externalEventId = interview.getExternalCalendarEventId();
        if (externalEventId != null && interview.getRecruiterId() != null) {
            employeeRepository.findById(interview.getRecruiterId()).ifPresent(recruiter -> {
                try {
                    integrationService.getMeetingProvider().cancelMeeting(recruiter.getEmail(), externalEventId, request.reason());
                } catch (Exception e) {
                    log.warn("Could not cancel external meeting {}: {}", externalEventId, e.getMessage());
                }
            });
        }

        String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
        notifyParticipants(interview, "Interview Cancelled",
                "The interview for " + candidateName + " scheduled on " + interview.getScheduledDate() + " has been cancelled."
                        + (request.reason() != null ? " Reason: " + request.reason() : ""));

        auditService.record("Interview Cancelled", candidateName,
                "Round " + interview.getRound() + (request.reason() != null ? " - " + request.reason() : ""));

        return mapInterview(interview);
    }

    public InterviewResponse submitFeedback(UUID id, InterviewFeedbackRequest request, UserPrincipal principal) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found"));
        Employee submitter = principal != null ? employeeRepository.findByUserId(principal.getId()).orElse(null) : null;

        interview.setFeedback(request.getFeedback());
        interview.setRating(request.getRating());
        interview.setResult(request.getResult());
        interview.setRecommendation(request.getRecommendation());
        interview.setTechnicalRating(request.getTechnicalRating());
        interview.setCommunicationRating(request.getCommunicationRating());
        interview.setProblemSolvingRating(request.getProblemSolvingRating());
        interview.setCodingRating(request.getCodingRating());
        interview.setArchitectureRating(request.getArchitectureRating());
        interview.setCultureFitRating(request.getCultureFitRating());
        interview.setStrengths(request.getStrengths());
        interview.setWeaknesses(request.getWeaknesses());
        if (request.getComments() != null) interview.setFeedback(request.getComments());
        interview.setSubmittedById(submitter != null ? submitter.getId() : null);
        interview.setSubmittedAt(LocalDateTime.now());
        interview.setStatus(Interview.Status.COMPLETED);
        interview = interviewRepository.save(interview);

        Candidate candidate = interview.getCandidate();
        String candidateName = candidate.getFirstName() + " " + candidate.getLastName();

        auditService.record("Interview Feedback Submitted", candidateName,
                "Round " + interview.getRound() + " - " + (request.getRecommendation() != null ? request.getRecommendation() : request.getResult()));
        auditService.record("Round Completed", candidateName, "Round " + interview.getRound());

        int completedRound = interview.getRound();
        if (interview.getRecruiterId() != null) {
            employeeRepository.findById(interview.getRecruiterId()).ifPresent(recruiter -> {
                if (recruiter.getUser() != null) {
                    notificationService.sendNotification(recruiter.getUser().getId(),
                            "Interview Feedback Submitted",
                            "Feedback for " + candidateName + "'s round " + completedRound + " has been submitted.",
                            Notification.NotificationType.RECRUITMENT, candidate.getId(), "CANDIDATE");
                }
            });
        }

        return mapInterview(interview);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> getUpcomingInterviews(UUID interviewerId) {
        return interviewRepository
                .findByInterviewerIdAndScheduledDateGreaterThanEqual(interviewerId, LocalDate.now())
                .stream().map(this::mapInterview).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> getCandidateInterviews(UUID candidateId) {
        return interviewRepository.findByCandidateIdOrderByScheduledDateAscScheduledTimeAsc(candidateId)
                .stream().map(this::mapInterview).toList();
    }

    // ─── Candidate timeline ───

    @Transactional(readOnly = true)
    public List<CandidateTimelineEntry> getCandidateTimeline(UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        List<CandidateTimelineEntry> entries = new ArrayList<>();

        entries.add(new CandidateTimelineEntry(candidate.getCreatedAt(), "CANDIDATE_CREATED",
                "Candidate Created", candidate.getFirstName() + " " + candidate.getLastName() + " added to the pipeline",
                null, null, null, null, null, null, null));

        for (Interview i : interviewRepository.findByCandidateId(candidateId)) {
            String interviewerName = i.getInterviewerName();
            String recruiterName = i.getRecruiterId() != null
                    ? employeeRepository.findById(i.getRecruiterId()).map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null)
                    : null;
            String label = (i.getTitle() != null && !i.getTitle().isBlank()) ? i.getTitle() : i.getType() + " - Round " + i.getRound();

            LocalDateTime scheduledAt = i.getScheduledDate() != null && i.getScheduledTime() != null
                    ? LocalDateTime.of(i.getScheduledDate(), i.getScheduledTime()) : i.getCreatedAt();

            String eventType = switch (i.getStatus()) {
                case CANCELLED -> "INTERVIEW_CANCELLED";
                case COMPLETED -> "INTERVIEW_COMPLETED";
                case RESCHEDULED -> "INTERVIEW_RESCHEDULED";
                default -> "INTERVIEW_SCHEDULED";
            };
            entries.add(new CandidateTimelineEntry(
                    i.getStatus() == Interview.Status.COMPLETED && i.getSubmittedAt() != null ? i.getSubmittedAt() : scheduledAt,
                    eventType, label,
                    i.getStatus() == Interview.Status.CANCELLED ? i.getCancellationReason() : null,
                    interviewerName, recruiterName, i.getDuration() > 0 ? i.getDuration() : null,
                    i.getRecommendation() != null ? i.getRecommendation().name() : (i.getResult() != null ? i.getResult().name() : null),
                    i.getNotes(), i.getFeedback(), i.getTechnicalRating()
            ));
        }

        for (CandidateFieldChange fc : candidateFieldChangeRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId)) {
            entries.add(new CandidateTimelineEntry(fc.getCreatedAt(), "FIELD_CHANGED",
                    fc.getFieldName() + " Changed", fc.getPreviousValue() + " -> " + fc.getNewValue(),
                    null, fc.getModifiedByName(), null, null, null, null, null));
        }

        if (candidate.getStatus() == Candidate.Status.SELECTED && candidate.getOfferedCtc() != null) {
            entries.add(new CandidateTimelineEntry(candidate.getUpdatedAt(), "OFFER_GENERATED",
                    "Offer Generated", "CTC " + candidate.getOfferedCtc(), null, null, null, null, null, null, null));
        }

        return entries.stream()
                .sorted(Comparator.comparing(CandidateTimelineEntry::timestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    // ─── Recruiter dashboard ───

    // Takes the User principal (not an Employee id directly) since every other caller of this
    // service only has the logged-in User's id - resolving to the Employee id here, once, avoids
    // every call site needing to do the same lookup (and getting it wrong, as the naive
    // `principal.getId()` pass-through would - that's a User id, not the Employee id that
    // Interview.recruiterId/Candidate.assignedRecruiterId actually store).
    @Transactional(readOnly = true)
    public RecruiterDashboardStats getRecruiterDashboardStats(UserPrincipal principal) {
        Employee recruiter = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employee profile not found for this user"));
        UUID recruiterEmployeeId = recruiter.getId();

        long upcomingInterviews = interviewRepository
                .findByRecruiterIdAndScheduledDateGreaterThanEqualAndStatus(recruiterEmployeeId, LocalDate.now(), Interview.Status.SCHEDULED)
                .size();
        long pendingFeedback = interviewRepository.findByRecruiterIdAndStatus(recruiterEmployeeId, Interview.Status.SCHEDULED).stream()
                .filter(i -> i.getScheduledDate() != null && i.getScheduledDate().isBefore(LocalDate.now().plusDays(1)) && i.getRecommendation() == null)
                .count();
        long offersPending = candidateRepository.countByAssignedRecruiterIdAndStatus(recruiterEmployeeId, Candidate.Status.PENDING_APPROVAL);
        long rejectedCandidates = candidateRepository.countByAssignedRecruiterIdAndStatus(recruiterEmployeeId, Candidate.Status.REJECTED);
        long waitingForScheduling = candidateRepository.countByAssignedRecruiterIdAndStatus(recruiterEmployeeId, Candidate.Status.SHORTLISTED);
        long waitingForHrApproval = candidateRepository.countByAssignedRecruiterIdAndStatus(recruiterEmployeeId, Candidate.Status.PENDING_APPROVAL);
        long waitingForManagerApproval = candidateRepository.countByAssignedRecruiterIdAndStatus(recruiterEmployeeId, Candidate.Status.REVISION_REQUESTED);
        long waitingForOffer = candidateRepository.countByAssignedRecruiterIdAndStatus(recruiterEmployeeId, Candidate.Status.SELECTED);

        return new RecruiterDashboardStats(upcomingInterviews, pendingFeedback, offersPending, rejectedCandidates,
                waitingForScheduling, waitingForHrApproval, waitingForManagerApproval, waitingForOffer);
    }

    // ─── Interview email/notification helper ───

    private void sendInterviewInviteAndNotify(Interview interview, Candidate candidate, JobPosting jobPosting, Employee scheduler, boolean isReschedule) {
        List<String> toEmails = new ArrayList<>();
        List<String> ccEmails = new ArrayList<>();
        if (candidate.getEmail() != null) toEmails.add(candidate.getEmail());

        Employee primaryInterviewer = interview.getInterviewerId() != null
                ? employeeRepository.findById(interview.getInterviewerId()).orElse(null) : null;
        if (primaryInterviewer != null && primaryInterviewer.getEmail() != null) toEmails.add(primaryInterviewer.getEmail());

        if (scheduler != null && scheduler.getEmail() != null) ccEmails.add(scheduler.getEmail());
        if (interview.getHrManagerId() != null) {
            employeeRepository.findById(interview.getHrManagerId())
                    .ifPresent(hr -> { if (hr.getEmail() != null) ccEmails.add(hr.getEmail()); });
        }
        for (UUID additionalId : interview.getAdditionalInterviewerIds()) {
            employeeRepository.findById(additionalId).ifPresent(e -> { if (e.getEmail() != null) ccEmails.add(e.getEmail()); });
        }

        String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
        String platformLabel = interview.getPlatform() != null ? interview.getPlatform().name().replace('_', ' ') : interview.getMode().name();

        // Step 1: create/update the actual meeting (Teams + calendar event) via whichever
        // MeetingProvider is configured - Recruitment never talks to Graph/Google directly, only
        // to this interface, per the integration abstraction layer.
        var meetingProvider = integrationService.getMeetingProvider();
        if (meetingProvider.isConfigured() && scheduler != null && scheduler.getEmail() != null) {
            List<String> attendees = new ArrayList<>(toEmails);
            attendees.addAll(ccEmails);
            var meetingRequest = new com.vikisol.one.integration.provider.MeetingRequest(
                    (interview.getTitle() != null ? interview.getTitle() : jobPosting.getTitle() + " - " + interview.getType() + " Interview"),
                    "Interview for " + jobPosting.getTitle(),
                    java.time.LocalDateTime.of(interview.getScheduledDate(), interview.getScheduledTime()),
                    java.time.LocalDateTime.of(interview.getScheduledDate(), interview.getScheduledTime()).plusMinutes(interview.getDuration() > 0 ? interview.getDuration() : 30),
                    interview.getTimezone(), attendees, scheduler.getEmail(), interview.getLocation(),
                    interview.getPlatform() != Interview.Platform.IN_PERSON && interview.getPlatform() != Interview.Platform.PHONE_CALL
            );
            try {
                var result = (isReschedule && interview.getExternalCalendarEventId() != null)
                        ? meetingProvider.updateMeeting(scheduler.getEmail(), interview.getExternalCalendarEventId(), meetingRequest)
                        : meetingProvider.createMeeting(meetingRequest);
                interview.setExternalCalendarEventId(result.calendarEventId());
                interview.setExternalMeetingId(result.meetingId());
                interview.setExternalTeamsMeetingId(result.teamsMeetingId());
                interview.setMeetingProviderName(meetingProvider.getProviderName());
                if (result.joinUrl() != null) interview.setMeetingLink(result.joinUrl());
                interview = interviewRepository.save(interview);
            } catch (Exception e) {
                log.warn("Meeting provider ({}) failed, falling back to manually-entered meeting link: {}", meetingProvider.getProviderName(), e.getMessage());
            }
        }

        // Step 2: send the invite - via the same provider's mailbox if it also implements
        // MailProvider (Microsoft 365 does), otherwise the default Resend account.
        if (!toEmails.isEmpty()) {
            var content = emailService.buildInterviewInviteEmail(
                    candidateName, jobPosting.getTitle(), jobPosting.getDescription(), jobPosting.getSkills(),
                    (isReschedule ? "[Rescheduled] " : "") + (interview.getTitle() != null ? interview.getTitle() : jobPosting.getTitle() + " - " + interview.getType() + " Interview"),
                    interview.getType() != null ? interview.getType().name() : "INTERVIEW",
                    interview.getScheduledDate(), interview.getScheduledTime(), interview.getDuration(), interview.getTimezone(),
                    platformLabel, interview.getMeetingLink(), interview.getLocation(), interview.getAgenda(),
                    scheduler != null ? scheduler.getFirstName() + " " + scheduler.getLastName() : null,
                    scheduler != null ? scheduler.getEmail() : null,
                    interview.getId().toString()
            );
            var attachments = List.of(new com.vikisol.one.integration.provider.MailMessage.Attachment("interview_invite.ics", content.ics()));
            var mailMessage = new com.vikisol.one.integration.provider.MailMessage(toEmails, ccEmails, content.subject(), content.html(), attachments,
                    scheduler != null ? scheduler.getEmail() : null);
            integrationService.getMailProvider().sendMail(mailMessage);
        }

        notifyParticipants(interview, isReschedule ? "Interview Rescheduled" : "Interview Scheduled",
                (isReschedule ? "Interview for " : "New interview for ") + candidateName + " on " + interview.getScheduledDate() + " at " + interview.getScheduledTime());
    }

    private void notifyParticipants(Interview interview, String title, String message) {
        List<UUID> employeeIdsToNotify = new ArrayList<>();
        if (interview.getInterviewerId() != null) employeeIdsToNotify.add(interview.getInterviewerId());
        if (interview.getRecruiterId() != null) employeeIdsToNotify.add(interview.getRecruiterId());
        if (interview.getHrManagerId() != null) employeeIdsToNotify.add(interview.getHrManagerId());
        employeeIdsToNotify.addAll(interview.getAdditionalInterviewerIds());

        for (UUID empId : employeeIdsToNotify.stream().distinct().toList()) {
            employeeRepository.findById(empId).ifPresent(emp -> {
                if (emp.getUser() != null) {
                    notificationService.sendNotification(emp.getUser().getId(), title, message,
                            Notification.NotificationType.RECRUITMENT, interview.getCandidate().getId(), "CANDIDATE");
                }
            });
        }
    }

    // ─── Mappers ───

    private JobPostingResponse mapJobPosting(JobPosting j) {
        JobPostingResponse r = new JobPostingResponse();
        r.setId(j.getId());
        r.setTitle(j.getTitle());
        r.setDescription(j.getDescription());
        if (j.getDepartment() != null) {
            r.setDepartmentId(j.getDepartment().getId());
            r.setDepartmentName(j.getDepartment().getName());
        }
        if (j.getDesignation() != null) {
            r.setDesignationId(j.getDesignation().getId());
            r.setDesignationTitle(j.getDesignation().getTitle());
        }
        r.setLocation(j.getLocation());
        r.setEmploymentType(j.getEmploymentType());
        r.setExperienceMin(j.getExperienceMin());
        r.setExperienceMax(j.getExperienceMax());
        r.setSalaryMin(j.getSalaryMin());
        r.setSalaryMax(j.getSalaryMax());
        r.setSkills(j.getSkills());
        r.setNumberOfPositions(j.getNumberOfPositions());
        r.setStatus(j.getStatus());
        r.setPostedById(j.getPostedById());
        r.setPostedDate(j.getPostedDate());
        r.setClosingDate(j.getClosingDate());
        r.setActive(j.isActive());
        r.setCreatedAt(j.getCreatedAt());
        r.setUpdatedAt(j.getUpdatedAt());
        return r;
    }

    private CandidateResponse mapCandidate(Candidate c) {
        CandidateResponse r = new CandidateResponse();
        r.setId(c.getId());
        r.setCandidateCode(c.getCandidateCode());
        r.setFirstName(c.getFirstName());
        r.setLastName(c.getLastName());
        r.setEmail(c.getEmail());
        r.setPhone(c.getPhone());
        r.setAlternateMobile(c.getAlternateMobile());
        r.setCurrentAddress(c.getCurrentAddress());
        r.setCity(c.getCity());
        r.setState(c.getState());
        r.setCountry(c.getCountry());
        r.setLinkedinUrl(c.getLinkedinUrl());
        r.setGithubUrl(c.getGithubUrl());
        r.setPortfolioUrl(c.getPortfolioUrl());
        r.setCurrentCompany(c.getCurrentCompany());
        r.setCurrentDesignation(c.getCurrentDesignation());
        r.setEmploymentType(c.getEmploymentType());
        r.setExperienceYears(c.getExperienceYears());
        r.setRelevantExperienceYears(c.getRelevantExperienceYears() != null ? c.getRelevantExperienceYears() : 0.0);
        r.setCertifications(c.getCertifications());
        r.setExpectedSalary(c.getExpectedSalary());
        r.setCurrentCtc(c.getCurrentCtc());
        r.setNoticePeriod(c.getNoticePeriod());
        r.setCurrentLocation(c.getCurrentLocation());
        r.setPreferredLocation(c.getPreferredLocation());
        r.setResumeUrl(c.getResumeUrl());
        r.setSkills(c.getSkills());
        r.setSource(c.getSource());
        r.setStatus(c.getStatus());
        r.setNotes(c.getNotes());
        if (c.getJobPosting() != null) {
            r.setJobPostingId(c.getJobPosting().getId());
            r.setJobPostingTitle(c.getJobPosting().getTitle());
            r.setJobPostingSkills(c.getJobPosting().getSkills());
            if (c.getJobPosting().getDepartment() != null) {
                r.setJobPostingDepartment(c.getJobPosting().getDepartment().getName());
            }
        }
        r.setOfferedCtc(c.getOfferedCtc());
        if (c.getOfferedDesignation() != null) {
            r.setOfferedDesignationId(c.getOfferedDesignation().getId());
            r.setOfferedDesignationTitle(c.getOfferedDesignation().getTitle());
        }
        if (c.getOfferedDepartment() != null) {
            r.setOfferedDepartmentId(c.getOfferedDepartment().getId());
            r.setOfferedDepartmentName(c.getOfferedDepartment().getName());
        }
        r.setOfferedDateOfJoining(c.getOfferedDateOfJoining());
        r.setOfferedReportingManagerId(c.getOfferedReportingManagerId());
        r.setOfferedJoiningBonus(c.getOfferedJoiningBonus());
        r.setOfferedVariablePay(c.getOfferedVariablePay());
        r.setConvertedEmployeeId(c.getConvertedEmployeeId());
        r.setManagerRemarks(c.getManagerRemarks());
        r.setAssignedRecruiterId(c.getAssignedRecruiterId());
        r.setHiringManagerId(c.getHiringManagerId());
        r.setBusinessUnit(c.getBusinessUnit());
        r.setPriority(c.getPriority());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
        return r;
    }

    private InterviewResponse mapInterview(Interview i) {
        InterviewResponse r = new InterviewResponse();
        r.setId(i.getId());
        if (i.getCandidate() != null) {
            r.setCandidateId(i.getCandidate().getId());
            r.setCandidateName(i.getCandidate().getFirstName() + " " + i.getCandidate().getLastName());
        }
        if (i.getJobPosting() != null) {
            r.setJobPostingId(i.getJobPosting().getId());
            r.setJobPostingTitle(i.getJobPosting().getTitle());
        }
        r.setTitle(i.getTitle());
        r.setType(i.getType());
        r.setInterviewerId(i.getInterviewerId());
        r.setInterviewerName(i.getInterviewerName());
        r.setAdditionalInterviewerIds(i.getAdditionalInterviewerIds());
        r.setRecruiterId(i.getRecruiterId());
        if (i.getRecruiterId() != null) {
            employeeRepository.findById(i.getRecruiterId())
                    .ifPresent(e -> r.setRecruiterName(e.getFirstName() + " " + e.getLastName()));
        }
        r.setHrManagerId(i.getHrManagerId());
        if (i.getHrManagerId() != null) {
            employeeRepository.findById(i.getHrManagerId())
                    .ifPresent(e -> r.setHrManagerName(e.getFirstName() + " " + e.getLastName()));
        }
        r.setRound(i.getRound());
        r.setOrderIndex(i.getOrderIndex() != null ? i.getOrderIndex() : 0);
        r.setScheduledDate(i.getScheduledDate());
        r.setScheduledTime(i.getScheduledTime());
        r.setDuration(i.getDuration());
        r.setTimezone(i.getTimezone());
        r.setMode(i.getMode());
        r.setPlatform(i.getPlatform());
        r.setMeetingLink(i.getMeetingLink());
        r.setExternalCalendarEventId(i.getExternalCalendarEventId());
        r.setExternalMeetingId(i.getExternalMeetingId());
        r.setExternalTeamsMeetingId(i.getExternalTeamsMeetingId());
        r.setMeetingProviderName(i.getMeetingProviderName());
        r.setLocation(i.getLocation());
        r.setNotes(i.getNotes());
        r.setAgenda(i.getAgenda());
        r.setPrepNotes(i.getPrepNotes());
        r.setAttachmentUrls(i.getAttachmentUrls());
        r.setStatus(i.getStatus());
        r.setCancellationReason(i.getCancellationReason());
        r.setFeedback(i.getFeedback());
        r.setRating(i.getRating());
        r.setResult(i.getResult());
        r.setRecommendation(i.getRecommendation());
        r.setTechnicalRating(i.getTechnicalRating());
        r.setCommunicationRating(i.getCommunicationRating());
        r.setProblemSolvingRating(i.getProblemSolvingRating());
        r.setCodingRating(i.getCodingRating());
        r.setArchitectureRating(i.getArchitectureRating());
        r.setCultureFitRating(i.getCultureFitRating());
        r.setStrengths(i.getStrengths());
        r.setWeaknesses(i.getWeaknesses());
        r.setSubmittedById(i.getSubmittedById());
        if (i.getSubmittedById() != null) {
            employeeRepository.findById(i.getSubmittedById())
                    .ifPresent(e -> r.setSubmittedByName(e.getFirstName() + " " + e.getLastName()));
        }
        r.setSubmittedAt(i.getSubmittedAt());
        r.setCreatedAt(i.getCreatedAt());
        r.setUpdatedAt(i.getUpdatedAt());
        return r;
    }
}
