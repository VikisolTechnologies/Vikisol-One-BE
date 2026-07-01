package com.vikisol.one.performance.service;

import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.common.exception.ResourceNotFoundException;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.performance.dto.*;
import com.vikisol.one.performance.entity.PerformanceGoal;
import com.vikisol.one.performance.entity.PerformanceReview;
import com.vikisol.one.performance.entity.ReviewCycle;
import com.vikisol.one.performance.repository.PerformanceGoalRepository;
import com.vikisol.one.performance.repository.PerformanceReviewRepository;
import com.vikisol.one.performance.repository.ReviewCycleRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PerformanceService {

    private final ReviewCycleRepository cycleRepository;
    private final PerformanceGoalRepository goalRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;

    public ReviewCycleResponse createCycle(ReviewCycleRequest request) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setName(request.getName());
        cycle.setStartDate(request.getStartDate());
        cycle.setEndDate(request.getEndDate());
        cycle.setStatus(ReviewCycle.Status.UPCOMING);
        cycle.setType(request.getType());
        return mapCycleResponse(cycleRepository.save(cycle));
    }

    public List<ReviewCycleResponse> getAllCycles() {
        return cycleRepository.findAll().stream().map(this::mapCycleResponse).toList();
    }

    public List<ReviewCycleResponse> getCyclesByStatus(ReviewCycle.Status status) {
        return cycleRepository.findByStatus(status).stream().map(this::mapCycleResponse).toList();
    }

    public GoalResponse createGoal(GoalRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ReviewCycle cycle = cycleRepository.findById(request.getReviewCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle not found"));

        PerformanceGoal goal = new PerformanceGoal();
        goal.setEmployee(employee);
        goal.setReviewCycle(cycle);
        goal.setTitle(request.getTitle());
        goal.setDescription(request.getDescription());
        goal.setCategory(request.getCategory());
        goal.setWeightage(request.getWeightage());
        goal.setTargetValue(request.getTargetValue());
        goal.setStatus(PerformanceGoal.Status.NOT_STARTED);
        goal.setDueDate(request.getDueDate());
        return mapGoalResponse(goalRepository.save(goal));
    }

    public List<GoalResponse> getMyGoals(UserPrincipal principal, UUID cycleId) {
        Employee emp = getEmployeeFromPrincipal(principal);
        return goalRepository.findByEmployeeIdAndReviewCycleId(emp.getId(), cycleId)
                .stream().map(this::mapGoalResponse).toList();
    }

    public List<GoalResponse> getTeamGoals(UserPrincipal principal, UUID cycleId) {
        Employee manager = getEmployeeFromPrincipal(principal);
        List<Employee> reports = employeeRepository.findByReportingManagerId(manager.getId());
        return reports.stream()
                .flatMap(e -> goalRepository.findByEmployeeIdAndReviewCycleId(e.getId(), cycleId).stream())
                .map(this::mapGoalResponse).toList();
    }

    public GoalResponse updateGoal(UUID goalId, GoalRequest request) {
        PerformanceGoal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        goal.setTitle(request.getTitle());
        goal.setDescription(request.getDescription());
        goal.setCategory(request.getCategory());
        goal.setWeightage(request.getWeightage());
        goal.setTargetValue(request.getTargetValue());
        goal.setDueDate(request.getDueDate());
        return mapGoalResponse(goalRepository.save(goal));
    }

    public PerformanceReviewResponse submitSelfReview(SelfReviewRequest request, UserPrincipal principal) {
        Employee emp = getEmployeeFromPrincipal(principal);
        ReviewCycle cycle = cycleRepository.findById(request.getReviewCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle not found"));

        PerformanceReview review = reviewRepository.findByEmployeeIdAndReviewCycleId(emp.getId(), cycle.getId())
                .orElseGet(() -> {
                    PerformanceReview r = new PerformanceReview();
                    r.setEmployee(emp);
                    r.setReviewCycle(cycle);
                    r.setReviewerId(emp.getReportingManagerId());
                    return r;
                });

        review.setOverallSelfRating(request.getOverallRating());
        review.setSelfSummary(request.getSummary());
        review.setStatus(PerformanceReview.Status.MANAGER_REVIEW);

        if (request.getGoalRatings() != null) {
            for (SelfReviewRequest.GoalRating gr : request.getGoalRatings()) {
                PerformanceGoal goal = goalRepository.findById(gr.getGoalId())
                        .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
                goal.setSelfRating(gr.getSelfRating());
                goal.setSelfComments(gr.getComments());
                goalRepository.save(goal);
            }
        }

        return mapReviewResponse(reviewRepository.save(review));
    }

    public PerformanceReviewResponse submitManagerReview(ManagerReviewRequest request, UserPrincipal principal) {
        Employee manager = getEmployeeFromPrincipal(principal);

        PerformanceReview review = reviewRepository.findByEmployeeIdAndReviewCycleId(
                        request.getEmployeeId(), request.getReviewCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getReviewerId().equals(manager.getId())) {
            throw new BadRequestException("You are not the reviewer for this employee");
        }

        review.setOverallManagerRating(request.getOverallRating());
        review.setManagerSummary(request.getSummary());
        review.setStrengths(request.getStrengths());
        review.setAreasOfImprovement(request.getAreasOfImprovement());
        review.setStatus(PerformanceReview.Status.COMPLETED);

        if (request.getGoalRatings() != null) {
            for (ManagerReviewRequest.GoalRating gr : request.getGoalRatings()) {
                PerformanceGoal goal = goalRepository.findById(gr.getGoalId())
                        .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
                goal.setManagerRating(gr.getManagerRating());
                goal.setManagerComments(gr.getComments());
                goalRepository.save(goal);
            }
        }

        return mapReviewResponse(reviewRepository.save(review));
    }

    public PerformanceReviewResponse acknowledgeReview(UUID reviewId, UserPrincipal principal) {
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        Employee emp = getEmployeeFromPrincipal(principal);
        if (!review.getEmployee().getId().equals(emp.getId())) {
            throw new BadRequestException("You can only acknowledge your own review");
        }
        review.setStatus(PerformanceReview.Status.ACKNOWLEDGED);
        review.setAcknowledgedDate(LocalDateTime.now());
        return mapReviewResponse(reviewRepository.save(review));
    }

    public List<PerformanceReviewResponse> getMyReviews(UserPrincipal principal) {
        Employee emp = getEmployeeFromPrincipal(principal);
        return reviewRepository.findAll().stream()
                .filter(r -> r.getEmployee().getId().equals(emp.getId()))
                .map(this::mapReviewResponse).toList();
    }

    public List<PerformanceReviewResponse> getTeamReviews(UserPrincipal principal, UUID cycleId) {
        Employee manager = getEmployeeFromPrincipal(principal);
        return reviewRepository.findByReviewerIdAndReviewCycleId(manager.getId(), cycleId)
                .stream().map(this::mapReviewResponse).toList();
    }

    private Employee getEmployeeFromPrincipal(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private ReviewCycleResponse mapCycleResponse(ReviewCycle c) {
        return ReviewCycleResponse.builder()
                .id(c.getId()).name(c.getName())
                .startDate(c.getStartDate()).endDate(c.getEndDate())
                .status(c.getStatus()).type(c.getType())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private GoalResponse mapGoalResponse(PerformanceGoal g) {
        return GoalResponse.builder()
                .id(g.getId()).employeeId(g.getEmployee().getId())
                .employeeName(g.getEmployee().getFirstName() + " " + g.getEmployee().getLastName())
                .reviewCycleId(g.getReviewCycle().getId())
                .reviewCycleName(g.getReviewCycle().getName())
                .title(g.getTitle()).description(g.getDescription())
                .category(g.getCategory()).weightage(g.getWeightage())
                .targetValue(g.getTargetValue()).achievedValue(g.getAchievedValue())
                .status(g.getStatus()).dueDate(g.getDueDate())
                .selfRating(g.getSelfRating()).managerRating(g.getManagerRating())
                .selfComments(g.getSelfComments()).managerComments(g.getManagerComments())
                .build();
    }

    private PerformanceReviewResponse mapReviewResponse(PerformanceReview r) {
        return PerformanceReviewResponse.builder()
                .id(r.getId()).employeeId(r.getEmployee().getId())
                .employeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName())
                .reviewCycleId(r.getReviewCycle().getId())
                .reviewCycleName(r.getReviewCycle().getName())
                .reviewerId(r.getReviewerId())
                .overallSelfRating(r.getOverallSelfRating())
                .overallManagerRating(r.getOverallManagerRating())
                .selfSummary(r.getSelfSummary()).managerSummary(r.getManagerSummary())
                .strengths(r.getStrengths()).areasOfImprovement(r.getAreasOfImprovement())
                .status(r.getStatus()).acknowledgedDate(r.getAcknowledgedDate())
                .build();
    }
}
