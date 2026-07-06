package com.vikisol.one.employee.service;

import com.vikisol.one.auth.entity.User;
import com.vikisol.one.employee.dto.AccountStatusResponse;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.settings.service.AuthSettingsService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// Backs the Employee Profile's "Linked Accounts" panel.
@Service
@RequiredArgsConstructor
public class AccountStatusService {

    private final EmployeeRepository employeeRepository;
    private final AuthSettingsService authSettingsService;

    @Transactional(readOnly = true)
    public AccountStatusResponse getAccountStatus(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        User user = employee.getUser();
        if (user == null) {
            return new AccountStatusResponse(employee.getEmail(), employee.getPersonalEmail(),
                    "NO_ACCOUNT", null, null, null, null,
                    authSettingsService.isMicrosoftLoginConfigured(), false, null);
        }

        String status;
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            status = "LOCKED";
        } else if (!user.isEnabled()) {
            status = "PENDING_ACTIVATION";
        } else if (!user.isAccountNonLocked()) {
            status = "DISABLED";
        } else {
            status = "ACTIVE";
        }

        return new AccountStatusResponse(
                user.getEmail(),
                employee.getPersonalEmail(),
                status,
                user.getLastLoginAt(),
                user.getPasswordChangedAt(),
                user.getLockedUntil(),
                user.getFailedLoginCount(),
                authSettingsService.isMicrosoftLoginConfigured(),
                false,  // Microsoft account linking is not implemented - honest, not a placeholder pretending otherwise.
                null
        );
    }
}
