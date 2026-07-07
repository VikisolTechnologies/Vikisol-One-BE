package com.vikisol.one.employee.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.employee.dto.*;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.service.OnboardingService;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Onboarding-wizard sub-resources (education/employment history/skills, profile completion) -
// an employee can manage their own; HR/CEO/Admin can manage anyone's (e.g. entering history on
// behalf of a new hire, or reviewing completion status for the whole team).
@RestController
@RequestMapping("/employees/{employeeId}/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final EmployeeRepository employeeRepository;

    private void assertSelfOrPrivileged(UUID employeeId, UserPrincipal principal) {
        boolean privileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (privileged) return;
        boolean isSelf = employeeRepository.findById(employeeId)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
        if (!isSelf) throw new BadRequestException("You can only manage your own onboarding profile");
    }

    // ─── Education ───

    @GetMapping("/education")
    public ResponseEntity<ApiResponse<List<EducationResponse>>> getEducation(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Education retrieved", onboardingService.getEducation(employeeId)));
    }

    @PostMapping("/education")
    public ResponseEntity<ApiResponse<EducationResponse>> addEducation(@PathVariable UUID employeeId, @RequestBody EducationRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Education added", onboardingService.addEducation(employeeId, request)));
    }

    @PutMapping("/education/{id}")
    public ResponseEntity<ApiResponse<EducationResponse>> updateEducation(@PathVariable UUID employeeId, @PathVariable UUID id, @RequestBody EducationRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Education updated", onboardingService.updateEducation(employeeId, id, request)));
    }

    @DeleteMapping("/education/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEducation(@PathVariable UUID employeeId, @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        onboardingService.deleteEducation(employeeId, id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Education removed", null));
    }

    // ─── Employment History ───

    @GetMapping("/employment-history")
    public ResponseEntity<ApiResponse<List<EmploymentHistoryResponse>>> getEmploymentHistory(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employment history retrieved", onboardingService.getEmploymentHistory(employeeId)));
    }

    @PostMapping("/employment-history")
    public ResponseEntity<ApiResponse<EmploymentHistoryResponse>> addEmploymentHistory(@PathVariable UUID employeeId, @RequestBody EmploymentHistoryRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employment history added", onboardingService.addEmploymentHistory(employeeId, request)));
    }

    @PutMapping("/employment-history/{id}")
    public ResponseEntity<ApiResponse<EmploymentHistoryResponse>> updateEmploymentHistory(@PathVariable UUID employeeId, @PathVariable UUID id, @RequestBody EmploymentHistoryRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employment history updated", onboardingService.updateEmploymentHistory(employeeId, id, request)));
    }

    @DeleteMapping("/employment-history/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEmploymentHistory(@PathVariable UUID employeeId, @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        onboardingService.deleteEmploymentHistory(employeeId, id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employment history removed", null));
    }

    // ─── Skills ───

    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<SkillResponse>>> getSkills(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Skills retrieved", onboardingService.getSkills(employeeId)));
    }

    @PostMapping("/skills")
    public ResponseEntity<ApiResponse<SkillResponse>> addSkill(@PathVariable UUID employeeId, @RequestBody SkillRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Skill added", onboardingService.addSkill(employeeId, request)));
    }

    @DeleteMapping("/skills/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(@PathVariable UUID employeeId, @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        onboardingService.deleteSkill(employeeId, id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Skill removed", null));
    }

    // ─── Nominees ───

    @GetMapping("/nominees")
    public ResponseEntity<ApiResponse<List<NomineeResponse>>> getNominees(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Nominees retrieved", onboardingService.getNominees(employeeId)));
    }

    @PostMapping("/nominees")
    public ResponseEntity<ApiResponse<NomineeResponse>> addNominee(@PathVariable UUID employeeId, @RequestBody NomineeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Nominee added", onboardingService.addNominee(employeeId, request)));
    }

    @PutMapping("/nominees/{id}")
    public ResponseEntity<ApiResponse<NomineeResponse>> updateNominee(@PathVariable UUID employeeId, @PathVariable UUID id, @RequestBody NomineeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Nominee updated", onboardingService.updateNominee(employeeId, id, request)));
    }

    @DeleteMapping("/nominees/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNominee(@PathVariable UUID employeeId, @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        onboardingService.deleteNominee(employeeId, id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Nominee removed", null));
    }

    // ─── Profile completion ───

    @GetMapping("/completion")
    public ResponseEntity<ApiResponse<ProfileCompletionResponse>> getProfileCompletion(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        assertSelfOrPrivileged(employeeId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile completion retrieved", onboardingService.getProfileCompletion(employeeId)));
    }
}
