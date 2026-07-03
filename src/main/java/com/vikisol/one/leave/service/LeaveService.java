package com.vikisol.one.leave.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.leave.dto.*;
import com.vikisol.one.leave.entity.LeaveBalance;
import com.vikisol.one.leave.entity.LeaveRequest;
import com.vikisol.one.leave.entity.LeaveType;
import com.vikisol.one.leave.repository.LeaveBalanceRepository;
import com.vikisol.one.leave.repository.LeaveRequestRepository;
import com.vikisol.one.leave.repository.LeaveTypeRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LeaveService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public LeaveTypeResponse createLeaveType(LeaveTypeRequest request) {
        LeaveType leaveType = LeaveType.builder()
                .name(request.name())
                .code(request.code())
                .defaultDays(request.defaultDays())
                .carryForward(request.carryForward())
                .maxCarryForwardDays(request.maxCarryForwardDays())
                .isActive(true)
                .build();

        leaveType = leaveTypeRepository.save(leaveType);
        return mapToLeaveTypeResponse(leaveType);
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getAllLeaveTypes() {
        return leaveTypeRepository.findByIsActiveTrue().stream()
                .map(this::mapToLeaveTypeResponse)
                .toList();
    }

    // Lets the CEO adjust the annual quota (e.g. Casual/Earned/Sick/Comp days) for an existing leave type.
    public LeaveTypeResponse updateLeaveType(UUID id, LeaveTypeRequest request) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave type not found"));
        leaveType.setName(request.name());
        leaveType.setCode(request.code());
        leaveType.setDefaultDays(request.defaultDays());
        leaveType.setCarryForward(request.carryForward());
        leaveType.setMaxCarryForwardDays(request.maxCarryForwardDays());
        return mapToLeaveTypeResponse(leaveTypeRepository.save(leaveType));
    }

    public void deleteLeaveType(UUID id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave type not found"));
        leaveType.setActive(false);
        leaveTypeRepository.save(leaveType);
    }

    public void initializeLeaveBalances(UUID employeeId, int year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<LeaveType> activeTypes = leaveTypeRepository.findByIsActiveTrue();

        for (LeaveType leaveType : activeTypes) {
            var existing = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveType.getId(), year);

            if (existing.isEmpty()) {
                LeaveBalance balance = LeaveBalance.builder()
                        .employee(employee)
                        .leaveType(leaveType)
                        .year(year)
                        .totalDays(leaveType.getDefaultDays())
                        .usedDays(0)
                        .remainingDays(leaveType.getDefaultDays())
                        .carryForwardDays(0)
                        .build();

                leaveBalanceRepository.save(balance);
            }
        }
    }

    public LeaveRequestResponse applyLeave(LeaveApplyRequest request, UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new RuntimeException("Leave type not found"));

        if (request.endDate().isBefore(request.startDate())) {
            throw new RuntimeException("End date cannot be before start date");
        }

        double numberOfDays = request.isHalfDay() ? 0.5 : calculateBusinessDays(request.startDate(), request.endDate());

        int year = request.startDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year)
                .orElseThrow(() -> new RuntimeException("Leave balance not found. Please initialize balances for year " + year));

        if (balance.getRemainingDays() < numberOfDays) {
            throw new RuntimeException("Insufficient leave balance. Available: " + balance.getRemainingDays() + ", Requested: " + numberOfDays);
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .leaveType(leaveType)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .numberOfDays(numberOfDays)
                .reason(request.reason())
                .status(LeaveRequest.LeaveStatus.PENDING)
                .isHalfDay(request.isHalfDay())
                .halfDayType(request.halfDayType() != null ? LeaveRequest.HalfDayType.valueOf(request.halfDayType()) : null)
                .appliedOn(LocalDateTime.now())
                .build();

        leaveRequest = leaveRequestRepository.save(leaveRequest);
        return mapToLeaveRequestResponse(leaveRequest);
    }

    public LeaveRequestResponse processLeaveAction(UUID requestId, LeaveActionRequest request, UserPrincipal principal) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (leaveRequest.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new RuntimeException("Leave request is not in PENDING status");
        }

        Employee approver = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Approver employee not found"));

        if ("APPROVE".equalsIgnoreCase(request.action())) {
            leaveRequest.setStatus(LeaveRequest.LeaveStatus.APPROVED);
            leaveRequest.setApprovedById(approver.getId());
            leaveRequest.setApproverComments(request.comments());

            // Deduct from balance
            int year = leaveRequest.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(leaveRequest.getEmployee().getId(), leaveRequest.getLeaveType().getId(), year)
                    .orElseThrow(() -> new RuntimeException("Leave balance not found"));

            balance.setUsedDays(balance.getUsedDays() + leaveRequest.getNumberOfDays());
            balance.setRemainingDays(balance.getTotalDays() + balance.getCarryForwardDays() - balance.getUsedDays());
            leaveBalanceRepository.save(balance);

        } else if ("REJECT".equalsIgnoreCase(request.action())) {
            leaveRequest.setStatus(LeaveRequest.LeaveStatus.REJECTED);
            leaveRequest.setApprovedById(approver.getId());
            leaveRequest.setApproverComments(request.comments());
        } else {
            throw new RuntimeException("Invalid action. Use APPROVE or REJECT");
        }

        leaveRequest = leaveRequestRepository.save(leaveRequest);
        auditService.record("Leave " + leaveRequest.getStatus(), leaveRequest.getEmployee().getEmployeeId(),
                leaveRequest.getEmployee().getFirstName() + " " + leaveRequest.getEmployee().getLastName()
                        + "'s " + leaveRequest.getLeaveType().getName() + " request (" + leaveRequest.getNumberOfDays() + " days)");
        return mapToLeaveRequestResponse(leaveRequest);
    }

    public void cancelLeave(UUID requestId, UserPrincipal principal) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!leaveRequest.getEmployee().getId().equals(employee.getId())) {
            throw new RuntimeException("You can only cancel your own leave requests");
        }

        if (leaveRequest.getStatus() == LeaveRequest.LeaveStatus.APPROVED) {
            // Restore balance
            int year = leaveRequest.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveRequest.getLeaveType().getId(), year)
                    .orElseThrow(() -> new RuntimeException("Leave balance not found"));

            balance.setUsedDays(balance.getUsedDays() - leaveRequest.getNumberOfDays());
            balance.setRemainingDays(balance.getTotalDays() + balance.getCarryForwardDays() - balance.getUsedDays());
            leaveBalanceRepository.save(balance);
        } else if (leaveRequest.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new RuntimeException("Only PENDING or APPROVED leave requests can be cancelled");
        }

        leaveRequest.setStatus(LeaveRequest.LeaveStatus.CANCELLED);
        leaveRequestRepository.save(leaveRequest);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveRequestResponse> getMyLeaveRequests(UserPrincipal principal, Pageable pageable) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Page<LeaveRequest> page = leaveRequestRepository.findByEmployeeId(employee.getId(), pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveRequestResponse> getPendingApprovals(UserPrincipal principal, Pageable pageable) {
        Employee manager = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Page<LeaveRequest> page = leaveRequestRepository.findPendingForApprover(manager.getId(), pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getMyBalances(UserPrincipal principal, int year) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return leaveBalanceRepository.findByEmployeeIdAndYear(employee.getId(), year).stream()
                .map(b -> new LeaveBalanceResponse(
                        b.getLeaveType().getName(),
                        b.getTotalDays(),
                        b.getUsedDays(),
                        b.getRemainingDays(),
                        b.getCarryForwardDays(),
                        b.getYear()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveRequestResponse> getTeamLeaveRequests(UserPrincipal principal, Pageable pageable) {
        Employee manager = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Page<LeaveRequest> page = leaveRequestRepository.findByEmployeeReportingManagerId(manager.getId(), pageable);
        return toPagedResponse(page);
    }

    // --- Helper methods ---

    private double calculateBusinessDays(LocalDate start, LocalDate end) {
        double count = 0;
        LocalDate date = start;
        while (!date.isAfter(end)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                count++;
            }
            date = date.plusDays(1);
        }
        return count;
    }

    private LeaveTypeResponse mapToLeaveTypeResponse(LeaveType lt) {
        return new LeaveTypeResponse(
                lt.getId(), lt.getName(), lt.getCode(), lt.getDefaultDays(),
                lt.isCarryForward(), lt.getMaxCarryForwardDays(), lt.isActive()
        );
    }

    private LeaveRequestResponse mapToLeaveRequestResponse(LeaveRequest lr) {
        Employee emp = lr.getEmployee();
        return new LeaveRequestResponse(
                lr.getId(),
                emp.getFirstName() + " " + emp.getLastName(),
                emp.getEmployeeId(),
                lr.getLeaveType().getName(),
                lr.getStartDate(),
                lr.getEndDate(),
                lr.getNumberOfDays(),
                lr.getReason(),
                lr.getStatus().name(),
                null, // approverName - would need to look up approver
                lr.getApproverComments(),
                lr.getAppliedOn(),
                lr.isHalfDay()
        );
    }

    private PagedResponse<LeaveRequestResponse> toPagedResponse(Page<LeaveRequest> page) {
        List<LeaveRequestResponse> content = page.getContent().stream()
                .map(this::mapToLeaveRequestResponse)
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
