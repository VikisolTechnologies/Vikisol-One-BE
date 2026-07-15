package com.vikisol.one.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.backup.dto.BackupData;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import com.vikisol.one.leave.entity.LeaveType;
import com.vikisol.one.leave.repository.LeaveTypeRepository;
import com.vikisol.one.payroll.entity.PayrollConfig;
import com.vikisol.one.payroll.repository.PayrollConfigRepository;
import com.vikisol.one.settings.entity.CompanySettings;
import com.vikisol.one.settings.entity.Holiday;
import com.vikisol.one.settings.entity.RolePermission;
import com.vikisol.one.settings.repository.CompanySettingsRepository;
import com.vikisol.one.settings.repository.HolidayRepository;
import com.vikisol.one.settings.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayRepository holidayRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PayrollConfigRepository payrollConfigRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    // Ignored on both insert and update: id/audit fields must never be copied verbatim from an
    // uploaded file - a restored row is either a fresh insert (own new id) or an update of an
    // existing row (keeps its own id/createdAt), never a forced overwrite of those.
    private static final String[] IGNORED = {"id", "createdAt", "createdBy", "updatedAt", "updatedBy"};

    public byte[] exportBackup() throws Exception {
        BackupData data = BackupData.of(
                departmentRepository.findAll(), designationRepository.findAll(), leaveTypeRepository.findAll(),
                holidayRepository.findAll(), companySettingsRepository.findAll(), rolePermissionRepository.findAll(),
                payrollConfigRepository.findAll());
        auditService.record("Backup Exported", "Configuration", data.departments().size() + " departments, "
                + data.designations().size() + " designations, " + data.leaveTypes().size() + " leave types, "
                + data.holidays().size() + " holidays, " + data.companySettings().size() + " settings, "
                + data.rolePermissions().size() + " role permissions, " + data.payrollConfigs().size() + " payroll configs");
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
    }

    @Transactional
    public void restoreBackup(byte[] json) throws Exception {
        BackupData data = objectMapper.readValue(json, BackupData.class);

        List<Department> existingDepts = departmentRepository.findAll();
        for (Department incoming : data.departments()) {
            upsert(existingDepts, d -> d.getCode() != null && d.getCode().equalsIgnoreCase(incoming.getCode()),
                    incoming, Department::new, departmentRepository::save);
        }

        List<Designation> existingDes = designationRepository.findAll();
        for (Designation incoming : data.designations()) {
            upsert(existingDes, d -> d.getTitle() != null && d.getTitle().equalsIgnoreCase(incoming.getTitle()),
                    incoming, Designation::new, designationRepository::save);
        }

        List<LeaveType> existingLeaveTypes = leaveTypeRepository.findAll();
        for (LeaveType incoming : data.leaveTypes()) {
            upsert(existingLeaveTypes, l -> l.getCode() != null && l.getCode().equalsIgnoreCase(incoming.getCode()),
                    incoming, LeaveType::new, leaveTypeRepository::save);
        }

        List<Holiday> existingHolidays = holidayRepository.findAll();
        for (Holiday incoming : data.holidays()) {
            upsert(existingHolidays, h -> h.getName() != null && h.getName().equalsIgnoreCase(incoming.getName())
                            && java.util.Objects.equals(h.getDate(), incoming.getDate()),
                    incoming, Holiday::new, holidayRepository::save);
        }

        List<CompanySettings> existingSettings = companySettingsRepository.findAll();
        for (CompanySettings incoming : data.companySettings()) {
            upsert(existingSettings, s -> s.getKey() != null && s.getKey().equalsIgnoreCase(incoming.getKey()),
                    incoming, CompanySettings::new, companySettingsRepository::save);
        }

        List<RolePermission> existingPerms = rolePermissionRepository.findAll();
        for (RolePermission incoming : data.rolePermissions()) {
            upsert(existingPerms, p -> p.getRole() == incoming.getRole() && java.util.Objects.equals(p.getModule(), incoming.getModule()),
                    incoming, RolePermission::new, rolePermissionRepository::save);
        }

        List<PayrollConfig> existingConfigs = payrollConfigRepository.findAll();
        for (PayrollConfig incoming : data.payrollConfigs()) {
            upsert(existingConfigs, c -> c.getKey() != null && c.getKey().equalsIgnoreCase(incoming.getKey()),
                    incoming, PayrollConfig::new, payrollConfigRepository::save);
        }

        auditService.record("Backup Restored", "Configuration", "Restored from backup dated " + data.exportedAt());
    }

    private <T> void upsert(List<T> existingRows, Predicate<T> matcher, T incoming,
                             java.util.function.Supplier<T> newInstance, java.util.function.Consumer<T> save) {
        T target = existingRows.stream().filter(matcher).findFirst().orElseGet(newInstance);
        BeanUtils.copyProperties(incoming, target, IGNORED);
        save.accept(target);
    }
}
