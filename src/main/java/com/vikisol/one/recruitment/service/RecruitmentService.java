package com.vikisol.one.recruitment.service;

import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.service.EmployeeService;
import com.vikisol.one.payroll.service.PayrollService;
import com.vikisol.one.recruitment.dto.*;
import com.vikisol.one.recruitment.entity.Candidate;
import com.vikisol.one.recruitment.entity.Interview;
import com.vikisol.one.recruitment.entity.JobPosting;
import com.vikisol.one.recruitment.repository.CandidateRepository;
import com.vikisol.one.recruitment.repository.InterviewRepository;
import com.vikisol.one.recruitment.repository.JobPostingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RecruitmentService {

    private final JobPostingRepository jobPostingRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final EntityManager entityManager;
    private final EmployeeService employeeService;
    private final PayrollService payrollService;
    private final EmailService emailService;

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
        return mapJobPosting(jobPostingRepository.save(job));
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

    public CandidateResponse createCandidate(CandidateRequest request) {
        Candidate candidate = Candidate.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .currentCompany(request.getCurrentCompany())
                .currentDesignation(request.getCurrentDesignation())
                .experienceYears(request.getExperienceYears())
                .expectedSalary(request.getExpectedSalary())
                .noticePeriod(request.getNoticePeriod())
                .resumeUrl(request.getResumeUrl())
                .skills(request.getSkills())
                .source(request.getSource() != null ? request.getSource() : Candidate.Source.DIRECT)
                .notes(request.getNotes())
                .build();
        if (request.getJobPostingId() != null) {
            candidate.setJobPosting(entityManager.getReference(JobPosting.class, request.getJobPostingId()));
        }
        return mapCandidate(candidateRepository.save(candidate));
    }

    public CandidateResponse updateCandidate(UUID id, CandidateRequest request) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        candidate.setFirstName(request.getFirstName());
        candidate.setLastName(request.getLastName());
        candidate.setEmail(request.getEmail());
        candidate.setPhone(request.getPhone());
        candidate.setCurrentCompany(request.getCurrentCompany());
        candidate.setCurrentDesignation(request.getCurrentDesignation());
        candidate.setExperienceYears(request.getExperienceYears());
        candidate.setExpectedSalary(request.getExpectedSalary());
        candidate.setNoticePeriod(request.getNoticePeriod());
        candidate.setResumeUrl(request.getResumeUrl());
        candidate.setSkills(request.getSkills());
        if (request.getSource() != null) candidate.setSource(request.getSource());
        candidate.setNotes(request.getNotes());
        if (request.getJobPostingId() != null) {
            candidate.setJobPosting(entityManager.getReference(JobPosting.class, request.getJobPostingId()));
        }
        return mapCandidate(candidateRepository.save(candidate));
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

    public CandidateResponse updateCandidateStatus(UUID id, Candidate.Status status) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        candidate.setStatus(status);
        return mapCandidate(candidateRepository.save(candidate));
    }

    public void deleteCandidate(UUID id) {
        candidateRepository.deleteById(id);
    }

    /**
     * Marks a candidate as SELECTED, generates their employee record + employee ID using the
     * CEO-defined standard CTC breakup, and emails the offer/congratulations letter.
     */
    public SelectCandidateResponse selectCandidate(UUID id, SelectCandidateRequest request) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));

        Designation designation = entityManager.getReference(Designation.class, request.designationId());
        Department department = entityManager.getReference(Department.class, request.departmentId());

        Map<String, BigDecimal> breakup = payrollService.computeCtcBreakup(request.offeredCtc());

        EmployeeRequest employeeRequest = new EmployeeRequest(
                null,
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getEmail(),
                candidate.getPhone(),
                null,
                null,
                request.departmentId(),
                request.designationId(),
                request.dateOfJoining(),
                null, null, null,
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
                breakup.get("grossSalary"),
                breakup.get("ctc")
        );
        EmployeeResponse employee = employeeService.createEmployee(employeeRequest);

        candidate.setStatus(Candidate.Status.SELECTED);
        candidate.setOfferedCtc(request.offeredCtc());
        candidate.setOfferedDesignation(designation);
        candidate.setOfferedDepartment(department);
        candidate.setOfferedDateOfJoining(request.dateOfJoining());
        candidate.setConvertedEmployeeId(employee.employeeId());
        candidateRepository.save(candidate);

        emailService.sendOfferLetterEmail(
                candidate.getEmail(),
                candidate.getFirstName() + " " + candidate.getLastName(),
                employee.employeeId(),
                employee.designationTitle(),
                request.offeredCtc(),
                breakup,
                request.dateOfJoining()
        );

        return new SelectCandidateResponse(
                candidate.getId(),
                employee.employeeId(),
                candidate.getFirstName() + " " + candidate.getLastName(),
                candidate.getEmail(),
                breakup,
                true
        );
    }

    // ─── Interviews ───

    public InterviewResponse scheduleInterview(InterviewRequest request) {
        Interview interview = Interview.builder()
                .candidate(entityManager.getReference(Candidate.class, request.getCandidateId()))
                .jobPosting(entityManager.getReference(JobPosting.class, request.getJobPostingId()))
                .interviewerId(request.getInterviewerId())
                .interviewerName(request.getInterviewerName())
                .round(request.getRound())
                .scheduledDate(request.getScheduledDate())
                .scheduledTime(request.getScheduledTime())
                .duration(request.getDuration())
                .mode(request.getMode() != null ? request.getMode() : Interview.Mode.VIDEO)
                .build();
        return mapInterview(interviewRepository.save(interview));
    }

    public InterviewResponse submitFeedback(UUID id, InterviewFeedbackRequest request) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found"));
        interview.setFeedback(request.getFeedback());
        interview.setRating(request.getRating());
        interview.setResult(request.getResult());
        interview.setStatus(Interview.Status.COMPLETED);
        return mapInterview(interviewRepository.save(interview));
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> getUpcomingInterviews(UUID interviewerId) {
        return interviewRepository
                .findByInterviewerIdAndScheduledDateGreaterThanEqual(interviewerId, LocalDate.now())
                .stream().map(this::mapInterview).toList();
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
        r.setFirstName(c.getFirstName());
        r.setLastName(c.getLastName());
        r.setEmail(c.getEmail());
        r.setPhone(c.getPhone());
        r.setCurrentCompany(c.getCurrentCompany());
        r.setCurrentDesignation(c.getCurrentDesignation());
        r.setExperienceYears(c.getExperienceYears());
        r.setExpectedSalary(c.getExpectedSalary());
        r.setNoticePeriod(c.getNoticePeriod());
        r.setResumeUrl(c.getResumeUrl());
        r.setSkills(c.getSkills());
        r.setSource(c.getSource());
        r.setStatus(c.getStatus());
        r.setNotes(c.getNotes());
        if (c.getJobPosting() != null) {
            r.setJobPostingId(c.getJobPosting().getId());
            r.setJobPostingTitle(c.getJobPosting().getTitle());
        }
        r.setOfferedCtc(c.getOfferedCtc());
        if (c.getOfferedDesignation() != null) {
            r.setOfferedDesignationId(c.getOfferedDesignation().getId());
            r.setOfferedDesignationTitle(c.getOfferedDesignation().getTitle());
        }
        r.setOfferedDateOfJoining(c.getOfferedDateOfJoining());
        r.setConvertedEmployeeId(c.getConvertedEmployeeId());
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
        r.setInterviewerId(i.getInterviewerId());
        r.setInterviewerName(i.getInterviewerName());
        r.setRound(i.getRound());
        r.setScheduledDate(i.getScheduledDate());
        r.setScheduledTime(i.getScheduledTime());
        r.setDuration(i.getDuration());
        r.setMode(i.getMode());
        r.setStatus(i.getStatus());
        r.setFeedback(i.getFeedback());
        r.setRating(i.getRating());
        r.setResult(i.getResult());
        r.setCreatedAt(i.getCreatedAt());
        r.setUpdatedAt(i.getUpdatedAt());
        return r;
    }
}
