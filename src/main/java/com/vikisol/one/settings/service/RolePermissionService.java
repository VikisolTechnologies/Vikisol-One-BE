package com.vikisol.one.settings.service;

import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.settings.dto.RolePermissionEntry;
import com.vikisol.one.settings.entity.RolePermission;
import com.vikisol.one.settings.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * CEO-configurable module visibility per role. CEO itself always has full access
 * (hardcoded, not DB-editable) so nobody - including a mistaken edit - can lock the
 * CEO account out of the application.
 */
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;

    public static final List<String> MODULES = List.of(
            "dashboard", "employees", "recruitment", "assessments", "new-hires", "background-verification", "offboarding", "hr-tasks", "analytics", "projects", "resources", "attendance",
            "leave", "payroll", "timesheets", "tickets", "assets", "performance",
            "org-chart", "reports", "documents", "policies", "communication", "settings", "security-center",
            "my-background-verification", "company-branding", "document-studio", "company-integrations"
    );

    private static final Map<RoleEnum, Set<String>> DEFAULTS = Map.of(
            RoleEnum.CEO, Set.copyOf(MODULES),
            RoleEnum.ADMIN, Set.of("dashboard", "employees", "recruitment", "assessments", "new-hires", "background-verification", "offboarding", "hr-tasks", "analytics", "projects", "resources", "timesheets", "leave", "attendance", "payroll", "tickets", "assets", "performance", "org-chart", "reports", "documents", "policies", "communication", "settings", "security-center", "company-branding", "document-studio", "company-integrations"),
            RoleEnum.HR_MANAGER, Set.of("dashboard", "employees", "recruitment", "assessments", "new-hires", "background-verification", "offboarding", "hr-tasks", "analytics", "projects", "resources", "timesheets", "leave", "attendance", "payroll", "tickets", "assets", "performance", "org-chart", "reports", "documents", "policies", "communication", "settings", "company-branding", "document-studio"),
            // Managers are operational leads, not part of hiring/compensation approval - "new-hires" belongs to HR only.
            // They do get "offboarding" (to act on their reports' exit workflow) but not BGV or new-hires.
            RoleEnum.MANAGER, Set.of("dashboard", "employees", "offboarding", "projects", "resources", "timesheets", "leave", "attendance", "tickets", "performance", "org-chart", "reports", "policies"),
            // Everyone needs to see and acknowledge company policies, so "policies" is included for every role.
            RoleEnum.EMPLOYEE, Set.of("dashboard", "offboarding", "projects", "timesheets", "leave", "attendance", "payroll", "tickets", "performance", "documents", "policies", "my-background-verification"),
            RoleEnum.RECRUITER, Set.of("dashboard", "recruitment", "assessments", "background-verification", "reports", "policies"),
            RoleEnum.FINANCE, Set.of("dashboard", "payroll", "reports", "policies")
    );

    /** Full role x module matrix, for the CEO's permissions editor. */
    @Transactional(readOnly = true)
    public List<RolePermissionEntry> getMatrix() {
        List<RolePermissionEntry> matrix = new ArrayList<>();
        for (RoleEnum role : RoleEnum.values()) {
            Set<String> visible = getVisibleModules(role);
            for (String module : MODULES) {
                matrix.add(new RolePermissionEntry(role, module, visible.contains(module)));
            }
        }
        return matrix;
    }

    /** The modules a given role can currently see - what the frontend sidebar/nav should honor. */
    @Transactional(readOnly = true)
    public Set<String> getVisibleModules(RoleEnum role) {
        if (role == RoleEnum.CEO) {
            return Set.copyOf(MODULES);
        }
        List<RolePermission> overrides = rolePermissionRepository.findByRole(role);
        if (overrides.isEmpty()) {
            return DEFAULTS.getOrDefault(role, Set.of("dashboard"));
        }
        Set<String> visible = new HashSet<>();
        for (RolePermission p : overrides) {
            if (p.isCanView()) visible.add(p.getModule());
        }
        return visible;
    }

    @Transactional
    public List<RolePermissionEntry> updateMatrix(List<RolePermissionEntry> updates) {
        for (RolePermissionEntry entry : updates) {
            if (entry.role() == RoleEnum.CEO) continue; // CEO access is not editable
            if (!MODULES.contains(entry.module())) continue;
            RolePermission existing = rolePermissionRepository.findByRoleAndModule(entry.role(), entry.module())
                    .orElse(RolePermission.builder().role(entry.role()).module(entry.module()).build());
            existing.setCanView(entry.canView());
            rolePermissionRepository.save(existing);
        }
        return getMatrix();
    }
}
