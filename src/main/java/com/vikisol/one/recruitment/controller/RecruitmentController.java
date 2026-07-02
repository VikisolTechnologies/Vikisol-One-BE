package com.vikisol.one.recruitment.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.recruitment.dto.*;
import com.vikisol.one.recruitment.entity.Candidate;
import com.vikisol.one.recruitment.service.RecruitmentService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recruitment")
@RequiredArgsConstructor
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    // ─── Job Postings ───

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Page<JobPostingResponse>>> getJobPostings(Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Job postings retrieved",
                recruitmentService.getActiveJobPostings(pageable)));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<JobPostingResponse>> getJobPosting(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Job posting retrieved",
                recruitmentService.getJobPosting(id)));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<JobPostingResponse>> createJobPosting(
            @Valid @RequestBody JobPostingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Job posting created",
                recruitmentService.createJobPosting(request, principal.getId())));
    }

    @PutMapping("/jobs/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<JobPostingResponse>> updateJobPosting(
            @PathVariable UUID id, @Valid @RequestBody JobPostingRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Job posting updated",
                recruitmentService.updateJobPosting(id, request)));
    }

    @DeleteMapping("/jobs/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteJobPosting(@PathVariable UUID id) {
        recruitmentService.deleteJobPosting(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Job posting deleted", null));
    }

    // ─── Candidates ───

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Page<CandidateResponse>>> getCandidates(Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidates retrieved",
                recruitmentService.getCandidates(pageable)));
    }

    @GetMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<CandidateResponse>> getCandidate(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate retrieved",
                recruitmentService.getCandidate(id)));
    }

    @PostMapping("/candidates")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<CandidateResponse>> createCandidate(
            @Valid @RequestBody CandidateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Candidate created",
                recruitmentService.createCandidate(request)));
    }

    @PutMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<CandidateResponse>> updateCandidate(
            @PathVariable UUID id, @Valid @RequestBody CandidateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate updated",
                recruitmentService.updateCandidate(id, request)));
    }

    @DeleteMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCandidate(@PathVariable UUID id) {
        recruitmentService.deleteCandidate(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate deleted", null));
    }

    @PutMapping("/candidates/{id}/status")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<CandidateResponse>> updateCandidateStatus(
            @PathVariable UUID id, @RequestParam Candidate.Status status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate status updated",
                recruitmentService.updateCandidateStatus(id, status)));
    }

    // Recruiter proposes CTC/designation/department/joining date. Does NOT send an offer - a
    // manager has to approve first. Also used by the recruiter to resubmit after a revision request.
    @PostMapping("/candidates/{id}/propose-selection")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<CandidateResponse>> proposeSelection(
            @PathVariable UUID id, @Valid @RequestBody SelectCandidateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Offer proposal submitted for manager approval",
                recruitmentService.proposeSelection(id, request, principal)));
    }

    // Manager approves a recruiter's proposal: generates the employee ID and emails the offer letter.
    @PostMapping("/candidates/{id}/approve-selection")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<SelectCandidateResponse>> approveSelection(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate approved and offer sent",
                recruitmentService.approveSelection(id)));
    }

    // Manager sends the proposal back to the recruiter with remarks (e.g. CTC too high).
    @PostMapping("/candidates/{id}/request-revision")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<CandidateResponse>> requestRevision(
            @PathVariable UUID id, @RequestBody RevisionRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Revision requested and recruiter notified",
                recruitmentService.requestRevision(id, request.remarks())));
    }

    // ─── Interviews ───

    @PostMapping("/interviews")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<InterviewResponse>> scheduleInterview(
            @Valid @RequestBody InterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Interview scheduled",
                recruitmentService.scheduleInterview(request)));
    }

    @PutMapping("/interviews/{id}/feedback")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<InterviewResponse>> submitFeedback(
            @PathVariable UUID id, @Valid @RequestBody InterviewFeedbackRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Feedback submitted",
                recruitmentService.submitFeedback(id, request)));
    }

    @GetMapping("/interviews/upcoming")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<List<InterviewResponse>>> getUpcomingInterviews(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Upcoming interviews retrieved",
                recruitmentService.getUpcomingInterviews(principal.getId())));
    }
}
