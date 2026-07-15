package com.vikisol.one.backup.dto;

import com.vikisol.one.department.entity.Department;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.leave.entity.LeaveType;
import com.vikisol.one.payroll.entity.PayrollConfig;
import com.vikisol.one.settings.entity.CompanySettings;
import com.vikisol.one.settings.entity.Holiday;
import com.vikisol.one.settings.entity.RolePermission;

import java.time.LocalDateTime;
import java.util.List;

// Deliberately scoped to CONFIGURATION data only (departments, designations, leave types,
// holidays, company/payroll settings, role permissions) - NOT employee/payroll PII. Restoring raw
// employee or payslip records from an arbitrary uploaded file onto a live 300-500-user production
// system is a categorically different, much riskier operation (silently overwriting real people's
// current data) than restoring org config, so it's out of scope for this self-service UI feature.
public record BackupData(
        String exportedAt,
        List<Department> departments,
        List<Designation> designations,
        List<LeaveType> leaveTypes,
        List<Holiday> holidays,
        List<CompanySettings> companySettings,
        List<RolePermission> rolePermissions,
        List<PayrollConfig> payrollConfigs
) {
    public static BackupData of(List<Department> d, List<Designation> des, List<LeaveType> lt,
                                 List<Holiday> h, List<CompanySettings> cs, List<RolePermission> rp,
                                 List<PayrollConfig> pc) {
        return new BackupData(LocalDateTime.now().toString(), d, des, lt, h, cs, rp, pc);
    }
}
