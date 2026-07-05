package com.vikisol.one.employee.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.employee.dto.TransferRequest;
import com.vikisol.one.employee.dto.TransferResponse;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.entity.EmployeeTransfer;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.repository.EmployeeTransferRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Organization movement history (department/manager/location/cost center/business unit changes)
// for existing active employees - a separate concern from Offboarding, which only covers exits.
// New dedicated entity/table rather than folding into flat Employee fields, mirroring how
// BackgroundCheck/OffboardingCase already model their own per-item workflow histories.
@Service
@RequiredArgsConstructor
public class EmployeeTransferService {

    private final EmployeeTransferRepository transferRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    @Transactional
    public TransferResponse initiateTransfer(UUID employeeId, TransferRequest request, UUID initiatedById) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (request.transferType() == null) {
            throw new BadRequestException("Transfer type is required");
        }
        if (request.newValue() == null || request.newValue().isBlank()) {
            throw new BadRequestException("A new value is required for this transfer");
        }

        String previousValue;
        String storedNewValue;

        switch (request.transferType()) {
            case DEPARTMENT -> {
                Department department = resolveDepartment(request.newValue());
                previousValue = employee.getDepartment() != null ? employee.getDepartment().getName() : null;
                employee.setDepartment(department);
                storedNewValue = department.getName();
            }
            case REPORTING_MANAGER -> {
                Employee manager = employeeRepository.findById(parseUuid(request.newValue(), "manager"))
                        .orElseThrow(() -> new BadRequestException("New reporting manager not found"));
                previousValue = employee.getReportingManagerId() != null
                        ? employeeRepository.findById(employee.getReportingManagerId())
                                .map(this::employeeName).orElse(null)
                        : null;
                employee.setReportingManagerId(manager.getId());
                storedNewValue = employeeName(manager);
            }
            case LOCATION -> {
                previousValue = employee.getCity();
                employee.setCity(request.newValue());
                storedNewValue = request.newValue();
            }
            case COST_CENTER -> {
                previousValue = employee.getCostCenter();
                employee.setCostCenter(request.newValue());
                storedNewValue = request.newValue();
            }
            case BUSINESS_UNIT -> {
                previousValue = employee.getBusinessUnit();
                employee.setBusinessUnit(request.newValue());
                storedNewValue = request.newValue();
            }
            default -> throw new BadRequestException("Unsupported transfer type");
        }

        employeeRepository.save(employee);

        EmployeeTransfer transfer = EmployeeTransfer.builder()
                .employeeId(employeeId)
                .transferType(request.transferType())
                .previousValue(previousValue)
                .newValue(storedNewValue)
                .effectiveDate(request.effectiveDate() != null ? request.effectiveDate() : java.time.LocalDate.now())
                .reason(request.reason())
                .initiatedById(initiatedById)
                .build();
        transfer = transferRepository.save(transfer);

        auditService.record("Employee Transfer", employee.getEmployeeId(),
                request.transferType() + ": " + (previousValue != null ? previousValue : "-") + " -> " + storedNewValue);

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getHistory(UUID employeeId) {
        return transferRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(this::toResponse).toList();
    }

    private Department resolveDepartment(String value) {
        try {
            UUID id = UUID.fromString(value);
            return departmentRepository.findById(id)
                    .orElseThrow(() -> new BadRequestException("Department not found"));
        } catch (IllegalArgumentException e) {
            // Fall back to matching by name for callers that pass a department name instead of an id.
            return departmentRepository.findByNameContainingIgnoreCase(value).stream().findFirst()
                    .orElseThrow(() -> new BadRequestException("Department not found"));
        }
    }

    private UUID parseUuid(String value, String fieldLabel) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + fieldLabel + " id");
        }
    }

    private String employeeName(Employee e) {
        return e.getFirstName() + " " + e.getLastName();
    }

    private TransferResponse toResponse(EmployeeTransfer t) {
        String initiatedByName = t.getInitiatedById() != null
                ? employeeRepository.findByUserId(t.getInitiatedById())
                        .or(() -> employeeRepository.findById(t.getInitiatedById()))
                        .map(this::employeeName).orElse(null)
                : null;
        return new TransferResponse(t.getId(), t.getEmployeeId(), t.getTransferType(), t.getPreviousValue(),
                t.getNewValue(), t.getEffectiveDate(), t.getReason(), t.getInitiatedById(), initiatedByName,
                t.getCreatedAt());
    }
}
