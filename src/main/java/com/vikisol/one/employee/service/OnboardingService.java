package com.vikisol.one.employee.service;

import com.vikisol.one.document.repository.DocumentRepository;
import com.vikisol.one.employee.dto.*;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.entity.EmployeeEducation;
import com.vikisol.one.employee.entity.EmployeeEmploymentHistory;
import com.vikisol.one.employee.entity.EmployeeSkill;
import com.vikisol.one.employee.repository.EmployeeEducationRepository;
import com.vikisol.one.employee.repository.EmployeeEmploymentHistoryRepository;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.repository.EmployeeSkillRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Owns the onboarding-wizard sub-resources (education/employment history/skills) and the
// profile-completion calculation - kept in the employee package rather than a new top-level
// module since all of it is one-to-many data hanging off Employee, not an independent domain.
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeEducationRepository educationRepository;
    private final EmployeeEmploymentHistoryRepository employmentHistoryRepository;
    private final EmployeeSkillRepository skillRepository;
    private final DocumentRepository documentRepository;

    private Employee getEmployeeOrThrow(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    // ─── Education ───

    @Transactional(readOnly = true)
    public List<EducationResponse> getEducation(UUID employeeId) {
        return educationRepository.findByEmployeeId(employeeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public EducationResponse addEducation(UUID employeeId, EducationRequest request) {
        Employee employee = getEmployeeOrThrow(employeeId);
        EmployeeEducation education = EmployeeEducation.builder()
                .employee(employee)
                .degree(request.degree())
                .university(request.university())
                .college(request.college())
                .yearOfCompletion(request.yearOfCompletion())
                .gradeOrPercentage(request.gradeOrPercentage())
                .certificateDocumentUrl(request.certificateDocumentUrl())
                .build();
        return toResponse(educationRepository.save(education));
    }

    @Transactional
    public void deleteEducation(UUID id) {
        educationRepository.deleteById(id);
    }

    private EducationResponse toResponse(EmployeeEducation e) {
        return new EducationResponse(e.getId(), e.getDegree(), e.getUniversity(), e.getCollege(),
                e.getYearOfCompletion(), e.getGradeOrPercentage(), e.getCertificateDocumentUrl());
    }

    // ─── Employment History ───

    @Transactional(readOnly = true)
    public List<EmploymentHistoryResponse> getEmploymentHistory(UUID employeeId) {
        return employmentHistoryRepository.findByEmployeeId(employeeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public EmploymentHistoryResponse addEmploymentHistory(UUID employeeId, EmploymentHistoryRequest request) {
        Employee employee = getEmployeeOrThrow(employeeId);
        EmployeeEmploymentHistory history = EmployeeEmploymentHistory.builder()
                .employee(employee)
                .companyName(request.companyName())
                .designation(request.designation())
                .joiningDate(request.joiningDate())
                .relievingDate(request.relievingDate())
                .skillsUsed(request.skillsUsed())
                .managerName(request.managerName())
                .reasonForLeaving(request.reasonForLeaving())
                .location(request.location())
                .lastSalary(request.lastSalary())
                .offerLetterUrl(request.offerLetterUrl())
                .experienceLetterUrl(request.experienceLetterUrl())
                .relievingLetterUrl(request.relievingLetterUrl())
                .build();
        return toResponse(employmentHistoryRepository.save(history));
    }

    @Transactional
    public void deleteEmploymentHistory(UUID id) {
        employmentHistoryRepository.deleteById(id);
    }

    private EmploymentHistoryResponse toResponse(EmployeeEmploymentHistory h) {
        return new EmploymentHistoryResponse(h.getId(), h.getCompanyName(), h.getDesignation(),
                h.getJoiningDate(), h.getRelievingDate(), h.getSkillsUsed(), h.getManagerName(),
                h.getReasonForLeaving(), h.getLocation(), h.getLastSalary(),
                h.getOfferLetterUrl(), h.getExperienceLetterUrl(), h.getRelievingLetterUrl());
    }

    // ─── Skills ───

    @Transactional(readOnly = true)
    public List<SkillResponse> getSkills(UUID employeeId) {
        return skillRepository.findByEmployeeId(employeeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SkillResponse addSkill(UUID employeeId, SkillRequest request) {
        Employee employee = getEmployeeOrThrow(employeeId);
        EmployeeSkill skill = EmployeeSkill.builder()
                .employee(employee)
                .skillName(request.skillName())
                .yearsOfExperience(request.yearsOfExperience())
                .level(request.level() != null ? request.level() : EmployeeSkill.Level.INTERMEDIATE)
                .lastUsed(request.lastUsed())
                .certified(request.certified() != null && request.certified())
                .build();
        return toResponse(skillRepository.save(skill));
    }

    @Transactional
    public void deleteSkill(UUID id) {
        skillRepository.deleteById(id);
    }

    private SkillResponse toResponse(EmployeeSkill s) {
        return new SkillResponse(s.getId(), s.getSkillName(), s.getYearsOfExperience(), s.getLevel(), s.getLastUsed(), s.isCertified());
    }

    // ─── Profile completion ───

    // Equal-weighted across 8 sections, matching the onboarding wizard's step list - each section
    // is "done" (contributes its full share) or "not done" (0), no partial credit within a
    // section, so the percentage always maps cleanly back to "which step is incomplete".
    @Transactional(readOnly = true)
    public ProfileCompletionResponse getProfileCompletion(UUID employeeId) {
        Employee e = getEmployeeOrThrow(employeeId);
        List<String> missing = new ArrayList<>();
        int sectionsDone = 0;
        final int totalSections = 8;

        boolean personalDone = e.getDateOfBirth() != null && e.getGender() != null && e.getCurrentAddress() != null
                && e.getPersonalEmail() != null && e.getPersonalMobile() != null && e.getProfilePictureUrl() != null;
        if (personalDone) sectionsDone++; else missing.add("Personal Information");

        boolean educationDone = educationRepository.countByEmployeeId(employeeId) > 0;
        if (educationDone) sectionsDone++; else missing.add("Education");

        boolean employmentDone = employmentHistoryRepository.countByEmployeeId(employeeId) > 0;
        if (employmentDone) sectionsDone++; else missing.add("Employment History");

        boolean skillsDone = skillRepository.countByEmployeeId(employeeId) > 0;
        if (skillsDone) sectionsDone++; else missing.add("Skills");

        boolean documentsDone = !documentRepository.findByEmployeeId(employeeId).isEmpty();
        if (documentsDone) sectionsDone++; else missing.add("Documents");

        boolean bankDone = e.getBankName() != null && e.getBankAccountNumber() != null && e.getIfscCode() != null;
        if (bankDone) sectionsDone++; else missing.add("Bank Details");

        boolean taxDone = e.getPanNumber() != null && e.getAadharNumber() != null && e.getUanNumber() != null;
        if (taxDone) sectionsDone++; else missing.add("Tax Information");

        boolean nomineeDone = e.getNomineeName() != null && e.getNomineeRelation() != null;
        if (nomineeDone) sectionsDone++; else missing.add("Nominee");

        int percent = (int) Math.round((sectionsDone * 100.0) / totalSections);
        return new ProfileCompletionResponse(percent, missing);
    }
}
