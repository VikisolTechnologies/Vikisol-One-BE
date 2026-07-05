package com.vikisol.one.assessment.service;

import com.vikisol.one.assessment.dto.AssessmentResponse;
import com.vikisol.one.assessment.dto.AssessmentWebhookRequest;
import com.vikisol.one.assessment.dto.MoveToInterviewRequest;
import com.vikisol.one.assessment.entity.Assessment;
import com.vikisol.one.assessment.repository.AssessmentRepository;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.recruitment.dto.InterviewRequest;
import com.vikisol.one.recruitment.entity.Candidate;
import com.vikisol.one.recruitment.repository.CandidateRepository;
import com.vikisol.one.recruitment.service.RecruitmentService;
import com.vikisol.one.security.RoleEnum;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AssessmentService {

    // Passing bar applied when Arena doesn't already tag the submission with a verdict -
    // kept configurable so it can be tuned per hiring drive without a redeploy.
    @Value("${assessment.pass-threshold-percent:60}")
    private double passThresholdPercent;

    private final AssessmentRepository assessmentRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final RecruitmentService recruitmentService;

    public AssessmentResponse ingestResult(AssessmentWebhookRequest request) {
        if (request.arenaSubmissionId() != null) {
            var existing = assessmentRepository.findByArenaSubmissionId(request.arenaSubmissionId());
            if (existing.isPresent()) {
                log.info("Ignoring duplicate Arena webhook for submission {}", request.arenaSubmissionId());
                return mapAssessment(existing.get());
            }
        }

        Candidate candidate = candidateRepository.findByEmail(request.candidateEmail()).orElseGet(() -> {
            String[] nameParts = request.candidateName().trim().split("\\s+", 2);
            Candidate created = Candidate.builder()
                    .firstName(nameParts[0])
                    .lastName(nameParts.length > 1 ? nameParts[1] : "")
                    .email(request.candidateEmail())
                    .phone(request.candidatePhone())
                    .experienceYears(request.yearsOfExperience())
                    .resumeUrl(request.resumeUrl())
                    .skills(request.techStack())
                    .source(Candidate.Source.PORTAL)
                    .notes("Auto-created from Vikisol Arena assessment submission")
                    .build();
            return candidateRepository.save(created);
        });

        double percentage = request.maxScore() > 0 ? (request.score() / request.maxScore()) * 100 : 0;
        Assessment.Status status = percentage >= passThresholdPercent ? Assessment.Status.PASS : Assessment.Status.FAIL;

        Assessment assessment = Assessment.builder()
                .candidate(candidate)
                .candidateName(request.candidateName())
                .candidateEmail(request.candidateEmail())
                .candidatePhone(request.candidatePhone())
                .yearsOfExperience(request.yearsOfExperience())
                .techStack(request.techStack())
                .resumeUrl(request.resumeUrl())
                .testName(request.testName())
                .dateTaken(request.dateTaken() != null ? request.dateTaken() : LocalDateTime.now())
                .score(request.score())
                .maxScore(request.maxScore())
                .percentage(percentage)
                .status(status)
                .arenaSubmissionId(request.arenaSubmissionId())
                .build();
        assessment = assessmentRepository.save(assessment);

        boolean passed = status == Assessment.Status.PASS;
        emailService.sendAssessmentResultEmail(candidate.getEmail(), request.candidateName(), request.testName(),
                request.score(), request.maxScore(), passed);

        userRepository.findByRoleIn(List.of(RoleEnum.RECRUITER, RoleEnum.HR_MANAGER, RoleEnum.CEO, RoleEnum.ADMIN))
                .forEach(user -> emailService.sendAssessmentNotificationEmail(
                        user.getEmail(), request.candidateName(), request.candidateEmail(),
                        request.testName(), request.score(), request.maxScore(), passed));

        return mapAssessment(assessment);
    }

    @Transactional(readOnly = true)
    public Page<AssessmentResponse> getAssessments(Pageable pageable) {
        return assessmentRepository.findAll(pageable).map(this::mapAssessment);
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getAssessment(UUID id) {
        return mapAssessment(assessmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assessment not found")));
    }

    public AssessmentResponse moveToInterview(UUID id, MoveToInterviewRequest request, com.vikisol.one.security.service.UserPrincipal principal) {
        Assessment assessment = assessmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assessment not found"));
        if (assessment.getCandidate() == null) {
            throw new IllegalStateException("This assessment has no linked candidate");
        }

        InterviewRequest interviewRequest = new InterviewRequest();
        interviewRequest.setCandidateId(assessment.getCandidate().getId());
        interviewRequest.setJobPostingId(request.jobPostingId());
        interviewRequest.setInterviewerId(request.interviewerId());
        interviewRequest.setInterviewerName(request.interviewerName());
        interviewRequest.setRound(request.round());
        interviewRequest.setScheduledDate(request.scheduledDate());
        interviewRequest.setScheduledTime(request.scheduledTime());
        interviewRequest.setDuration(request.duration());
        interviewRequest.setMode(request.mode());
        recruitmentService.scheduleInterview(interviewRequest, principal);

        Candidate candidate = assessment.getCandidate();
        candidate.setStatus(Candidate.Status.INTERVIEW_SCHEDULED);
        candidateRepository.save(candidate);

        assessment.setMovedToInterview(true);
        return mapAssessment(assessmentRepository.save(assessment));
    }

    private AssessmentResponse mapAssessment(Assessment a) {
        return new AssessmentResponse(
                a.getId(),
                a.getCandidate() != null ? a.getCandidate().getId() : null,
                a.getCandidateName(),
                a.getCandidateEmail(),
                a.getCandidatePhone(),
                a.getYearsOfExperience(),
                a.getTechStack(),
                a.getResumeUrl(),
                a.getTestName(),
                a.getDateTaken(),
                a.getScore(),
                a.getMaxScore(),
                a.getPercentage(),
                a.getStatus(),
                a.isMovedToInterview(),
                a.getCreatedAt()
        );
    }
}
