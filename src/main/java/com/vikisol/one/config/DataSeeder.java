package com.vikisol.one.config;

import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.one.doctemplate.dto.DocumentTemplateRequest;
import com.vikisol.one.doctemplate.entity.DocumentTemplate;
import com.vikisol.one.doctemplate.entity.TemplateVariable;
import com.vikisol.one.doctemplate.repository.DocumentTemplateRepository;
import com.vikisol.one.doctemplate.repository.TemplateVariableRepository;
import com.vikisol.one.doctemplate.service.DocumentTemplateService;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.leave.entity.LeaveType;
import com.vikisol.one.leave.repository.LeaveTypeRepository;
import com.vikisol.one.payroll.entity.PayrollConfig;
import com.vikisol.one.payroll.repository.PayrollConfigRepository;
import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.settings.entity.Holiday;
import com.vikisol.one.settings.repository.HolidayRepository;
import com.vikisol.one.settings.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.vikisol.one.settings.entity.Holiday.HolidayType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PayrollConfigRepository payrollConfigRepository;
    private final HolidayRepository holidayRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final ScheduledTasks scheduledTasks;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final DocumentTemplateService documentTemplateService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        // Hibernate 6 auto-generates a CHECK constraint on @Enumerated(STRING) columns matching the
        // enum values at the time the column was first created, but ddl-auto=update never refreshes
        // that constraint when new enum values are added later - so old constraints silently start
        // rejecting valid new statuses (e.g. Candidate.Status.PENDING_APPROVAL). Drop and let Hibernate
        // recreate them fresh against the current enum on next schema validation.
        dropStaleCheckConstraint("candidates", "candidates_status_check");

        // Same issue as above: RELIEVING_LETTER/SALARY_CERTIFICATE were added to
        // Document.DocumentType after this column's CHECK constraint was first generated, so
        // Postgres was still rejecting those two new values on insert.
        dropStaleCheckConstraint("documents", "documents_type_check");

        // body_html was NOT NULL when document_templates was first created (before the block
        // model existed); ddl-auto=update never relaxes an existing NOT NULL when a Java field's
        // annotation is loosened, so every block-based template (which legitimately has a null
        // bodyHtml, using contentBlocksJson instead) failed to insert with a real constraint
        // violation in prod. Drop it explicitly - the entity itself no longer declares it.
        dropNotNullConstraint("document_templates", "body_html");

        // is_active was the original boolean column before it was replaced by the `status` enum
        // column - ddl-auto=update leaves orphaned columns in place indefinitely (it only adds,
        // never drops/alters), so this stale NOT NULL column kept rejecting every insert even
        // after the Java field was gone. Drop the column outright since nothing reads it anymore.
        dropColumn("document_templates", "is_active");

        // Same recurring issue as candidates_status_check/documents_type_check: the CHECK
        // constraint on document_type was generated against only the enum values that existed
        // when this table was first created, and never refreshes when new DocumentType values
        // are added (13 more added alongside this Document Engine expansion).
        dropStaleCheckConstraint("document_templates", "document_templates_document_type_check");

        // Managers were briefly granted the "new-hires" (offer approval) module - that's now HR-only.
        // A stored override for any module on a role bypasses the whole DEFAULTS fallback, so this
        // stale row must be removed explicitly rather than relying on the code default alone.
        rolePermissionRepository.findByRoleAndModule(RoleEnum.MANAGER, "new-hires")
                .ifPresent(rolePermissionRepository::delete);

        if (userRepository.count() == 0) {
            log.info("Seeding initial data...");
            seedUsers();
            seedDepartments();
            seedDesignations();
            seedLeaveTypes();
            seedPayrollConfigs();
            seedHolidays();
            log.info("Data seeding completed.");
        } else {
            log.info("Data already seeded, skipping bulk seed...");
        }
        // Always run — idempotent, links any user that lacks an Employee record
        seedEmployeesForUsers();

        // Always run — idempotent, only inserts a default template for a document type if none
        // exists yet. Lets Document Studio ship with working templates out of the box instead of
        // every document type failing with "no published template" until an admin manually creates one.
        // Must run BEFORE seedDocumentTemplates(), which queries by status - the 4 templates
        // seeded before the status/templateGroupId columns existed would otherwise be invisible.
        migrateLegacyDocumentTemplates();
        seedDocumentTemplates();
        seedTemplateVariables();
        // Document templates are business content (legal letter text), not application config -
        // they must never be silently auto-created/changed in production. Auto-seed only in
        // non-production profiles for local/dev convenience; in prod, CEO/HR Manager must trigger
        // this deliberately via POST /admin/document-templates/seed-offer-letter (see
        // AdminTemplateSeedController), going through the exact same createDraft()/publish() flow
        // so it's still versioned/auditable, just never automatic.
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            seedOfferLetterTemplate();
        }

        // initializeYearlyLeaveBalances() only ran via @Scheduled(cron = "0 0 0 1 1 *") - literally
        // only at midnight Jan 1st. Since this app has never been running at that exact moment,
        // every employee had ZERO leave balance for the entire year, and applying for leave failed
        // outright ("Leave balance not found") for 100% of employees. The method is idempotent
        // (skips any employee/leaveType/year combo that already has a balance), so it's safe to
        // also run it here on every startup as a catch-up, not just wait for the next Jan 1.
        scheduledTasks.initializeYearlyLeaveBalances();
    }

    private void dropStaleCheckConstraint(String table, String constraintName) {
        try {
            entityManager.createNativeQuery(
                    "ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName)
                    .executeUpdate();
        } catch (Exception e) {
            log.warn("Could not drop constraint {} on {}: {}", constraintName, table, e.getMessage());
        }
    }

    private void dropNotNullConstraint(String table, String column) {
        try {
            entityManager.createNativeQuery(
                    "ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL")
                    .executeUpdate();
        } catch (Exception e) {
            log.warn("Could not drop NOT NULL on {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void dropColumn(String table, String column) {
        try {
            entityManager.createNativeQuery(
                    "ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + column)
                    .executeUpdate();
        } catch (Exception e) {
            log.warn("Could not drop column {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void seedUsers() {
        createUser("admin@vikisol.in", "Admin@123", "System", "Admin", RoleEnum.ADMIN);
        createUser("ceo@vikisol.in", "Ceo@123", "Vikram", "Singh", RoleEnum.CEO);
        createUser("hr@vikisol.in", "Hr@123", "Priya", "Sharma", RoleEnum.HR_MANAGER);
        createUser("finance@vikisol.in", "Finance@123", "Rajesh", "Kumar", RoleEnum.FINANCE);
        createUser("manager@vikisol.in", "Manager@123", "Amit", "Patel", RoleEnum.MANAGER);
        createUser("recruiter@vikisol.in", "Recruiter@123", "Sneha", "Reddy", RoleEnum.RECRUITER);
        createUser("employee@vikisol.in", "Employee@123", "Rahul", "Verma", RoleEnum.EMPLOYEE);
        log.info("  -> 7 users created");
    }

    private void createUser(String email, String password, String firstName, String lastName, RoleEnum role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setEnabled(true);
        user.setAccountNonLocked(true);
        userRepository.save(user);
    }

    private void seedDepartments() {
        createDept("Engineering", "ENG", "Software Engineering & Development");
        createDept("Human Resources", "HR", "People Operations & HR");
        createDept("Finance", "FIN", "Finance & Accounting");
        createDept("Marketing", "MKT", "Marketing & Communications");
        createDept("Sales", "SAL", "Sales & Business Development");
        createDept("Operations", "OPS", "Operations & Administration");
        createDept("Quality Assurance", "QA", "Testing & Quality Assurance");
        createDept("Design", "DES", "UI/UX Design");
        createDept("DevOps", "DOP", "DevOps & Infrastructure");
        createDept("Support", "SUP", "Customer Support");
        log.info("  -> 10 departments created");
    }

    private void createDept(String name, String code, String description) {
        Department dept = new Department();
        dept.setName(name);
        dept.setCode(code);
        dept.setDescription(description);
        dept.setActive(true);
        departmentRepository.save(dept);
    }

    private void seedDesignations() {
        createDesig("Chief Executive Officer", 1, "Top executive");
        createDesig("Chief Technology Officer", 2, "Head of technology");
        createDesig("Vice President", 3, "VP level");
        createDesig("Director", 4, "Director level");
        createDesig("Senior Manager", 5, "Senior management");
        createDesig("Manager", 6, "Mid management");
        createDesig("Team Lead", 7, "Team leadership");
        createDesig("Senior Software Engineer", 8, "Senior IC");
        createDesig("Software Engineer", 9, "Mid-level IC");
        createDesig("Junior Software Engineer", 10, "Entry level");
        createDesig("HR Manager", 6, "HR management");
        createDesig("HR Executive", 9, "HR operations");
        createDesig("Finance Manager", 6, "Finance management");
        createDesig("Accountant", 9, "Accounting");
        createDesig("Recruiter", 8, "Talent acquisition");
        createDesig("QA Engineer", 9, "Quality assurance");
        createDesig("DevOps Engineer", 8, "Infrastructure");
        createDesig("UI/UX Designer", 9, "Design");
        createDesig("Intern", 11, "Internship");
        log.info("  -> 19 designations created");
    }

    private void createDesig(String title, int level, String description) {
        Designation d = new Designation();
        d.setTitle(title);
        d.setLevel(level);
        d.setDescription(description);
        d.setActive(true);
        designationRepository.save(d);
    }

    private void seedLeaveTypes() {
        createLeaveType("Casual Leave", "CL", 12, false, 0);
        createLeaveType("Sick Leave", "SL", 12, false, 0);
        createLeaveType("Earned Leave", "EL", 15, true, 30);
        createLeaveType("Maternity Leave", "ML", 182, false, 0);
        createLeaveType("Paternity Leave", "PL", 15, false, 0);
        createLeaveType("Compensatory Off", "CO", 0, false, 0);
        createLeaveType("Loss of Pay", "LOP", 0, false, 0);
        createLeaveType("Marriage Leave", "MRL", 3, false, 0);
        createLeaveType("Bereavement Leave", "BL", 5, false, 0);
        log.info("  -> 9 leave types created");
    }

    private void createLeaveType(String name, String code, int days, boolean carryForward, int maxCF) {
        LeaveType lt = new LeaveType();
        lt.setName(name);
        lt.setCode(code);
        lt.setDefaultDays(days);
        lt.setCarryForward(carryForward);
        lt.setMaxCarryForwardDays(maxCF);
        lt.setActive(true);
        leaveTypeRepository.save(lt);
    }

    private void seedPayrollConfigs() {
        createConfig("PF_EMPLOYEE_RATE", "12", "Employee PF contribution %", "PF");
        createConfig("PF_EMPLOYER_RATE", "12", "Employer PF contribution %", "PF");
        createConfig("PF_BASIC_LIMIT", "15000", "PF basic salary cap", "PF");
        createConfig("ESI_EMPLOYEE_RATE", "0.75", "Employee ESI contribution %", "ESI");
        createConfig("ESI_EMPLOYER_RATE", "3.25", "Employer ESI contribution %", "ESI");
        createConfig("ESI_THRESHOLD", "21000", "ESI applicability threshold", "ESI");
        createConfig("PROFESSIONAL_TAX", "200", "Monthly professional tax", "TAX");
        createConfig("TDS_STANDARD_DEDUCTION", "75000", "Annual standard deduction for TDS", "TAX");
        log.info("  -> 8 payroll configs created");
    }

    private void createConfig(String key, String value, String desc, String category) {
        PayrollConfig config = new PayrollConfig();
        config.setKey(key);
        config.setValue(value);
        config.setDescription(desc);
        config.setCategory(category);
        payrollConfigRepository.save(config);
    }

    private void seedHolidays() {
        int year = LocalDate.now().getYear();
        createHoliday("New Year's Day", LocalDate.of(year, 1, 1), HolidayType.NATIONAL, year);
        createHoliday("Republic Day", LocalDate.of(year, 1, 26), HolidayType.NATIONAL, year);
        createHoliday("Holi", LocalDate.of(year, 3, 14), HolidayType.NATIONAL, year);
        createHoliday("Good Friday", LocalDate.of(year, 4, 18), HolidayType.OPTIONAL, year);
        createHoliday("May Day", LocalDate.of(year, 5, 1), HolidayType.NATIONAL, year);
        createHoliday("Independence Day", LocalDate.of(year, 8, 15), HolidayType.NATIONAL, year);
        createHoliday("Ganesh Chaturthi", LocalDate.of(year, 8, 27), HolidayType.OPTIONAL, year);
        createHoliday("Gandhi Jayanti", LocalDate.of(year, 10, 2), HolidayType.NATIONAL, year);
        createHoliday("Diwali", LocalDate.of(year, 10, 20), HolidayType.NATIONAL, year);
        createHoliday("Christmas", LocalDate.of(year, 12, 25), HolidayType.NATIONAL, year);
        log.info("  -> 10 holidays created for " + year);
    }

    private void createHoliday(String name, LocalDate date, HolidayType type, int year) {
        Holiday h = new Holiday();
        h.setName(name);
        h.setDate(date);
        h.setType(type);
        h.setYear(year);
        h.setOptional(type == HolidayType.OPTIONAL);
        holidayRepository.save(h);
    }

    // Backfills templateGroupId/status on the 4 template rows created before those columns
    // existed (Offer/Experience/Relieving-adjacent seed data) - without this they'd have
    // status=null and silently disappear from every "find the PUBLISHED template" query.
    private void migrateLegacyDocumentTemplates() {
        List<DocumentTemplate> legacy = documentTemplateRepository.findAll().stream()
                .filter(t -> t.getStatus() == null || t.getTemplateGroupId() == null || t.getTemplateGroupId().isBlank())
                .toList();
        for (DocumentTemplate t : legacy) {
            if (t.getStatus() == null) t.setStatus(DocumentTemplate.TemplateStatus.PUBLISHED);
            if (t.getTemplateGroupId() == null || t.getTemplateGroupId().isBlank()) t.setTemplateGroupId(java.util.UUID.randomUUID().toString());
        }
        if (!legacy.isEmpty()) {
            documentTemplateRepository.saveAll(legacy);
            log.info("Migrated {} legacy document templates to status/templateGroupId model", legacy.size());
        }
    }

    // Seeds one default, working template per document type Document Studio ships with - each
    // uses the same {{Placeholder}} + shared-chrome engine as every other template, so these are
    // just data, not special-cased Java code. An admin can create new versions/types later
    // without touching this method.
    private void seedDocumentTemplates() {
        seedTemplateIfMissing(Document.DocumentType.EXPERIENCE_LETTER, "Standard Experience Letter",
                "<h1 style=\"font-size:18px;letter-spacing:1px;text-align:center;margin:0 0 4px;\">EXPERIENCE LETTER</h1>"
                + "<p style=\"text-align:center;color:#777;font-size:11px;margin:0 0 28px;\">Date: {{CurrentDate}}</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">To Whomsoever It May Concern,</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">This is to certify that <b>{{EmployeeName}}</b> (Employee ID: <b>{{EmployeeID}}</b>) was employed with <b>{{CompanyName}}</b> as <b>{{Designation}}</b> in the <b>{{Department}}</b> department from <b>{{JoiningDate}}</b> to <b>{{LastWorkingDate}}</b>.</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">During this tenure, we found {{EmployeeName}} to be sincere, hardworking, and professional in conduct. We wish {{EmployeeName}} success in all future endeavors.</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 32px;\">This certificate is issued upon request for whatever purpose it may serve.</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"font-size:11px;\">For <b>{{CompanyName}}</b><br/><br/><br/>_____________________<br/>{{HRName}}<br/>Human Resources</td></tr></table>");

        seedTemplateIfMissing(Document.DocumentType.RELIEVING_LETTER, "Standard Relieving Letter",
                "<h1 style=\"font-size:18px;letter-spacing:1px;text-align:center;margin:0 0 4px;\">RELIEVING LETTER</h1>"
                + "<p style=\"text-align:center;color:#777;font-size:11px;margin:0 0 28px;\">Date: {{CurrentDate}}</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">Dear {{EmployeeName}},</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">This is to confirm that your resignation from the position of <b>{{Designation}}</b> at <b>{{CompanyName}}</b> has been accepted, and you are relieved of your duties effective <b>{{LastWorkingDate}}</b>.</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">Your Employee ID <b>{{EmployeeID}}</b> stands closed as of this date. All dues, if any, will be settled as per the Full &amp; Final settlement process.</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 32px;\">We thank you for your contribution during your tenure and wish you the very best in your future endeavors.</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"font-size:11px;\">For <b>{{CompanyName}}</b><br/><br/><br/>_____________________<br/>{{HRName}}<br/>Human Resources</td></tr></table>");

        seedTemplateIfMissing(Document.DocumentType.SALARY_CERTIFICATE, "Standard Salary Certificate",
                "<h1 style=\"font-size:18px;letter-spacing:1px;text-align:center;margin:0 0 4px;\">SALARY CERTIFICATE</h1>"
                + "<p style=\"text-align:center;color:#777;font-size:11px;margin:0 0 28px;\">Date: {{CurrentDate}}</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">To Whomsoever It May Concern,</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">This is to certify that <b>{{EmployeeName}}</b> (Employee ID: <b>{{EmployeeID}}</b>) is employed with <b>{{CompanyName}}</b> as <b>{{Designation}}</b> in the <b>{{Department}}</b> department since <b>{{JoiningDate}}</b>.</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:6px;padding:14px 18px;margin:0 0 20px;\">"
                + "<tr><td colspan=\"2\" style=\"padding-bottom:8px;\"><b style=\"font-size:12px;\">Compensation Details</b></td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;padding:3px 0;\">Monthly Gross Salary</td><td style=\"font-size:11px;font-weight:bold;text-align:right;\">Rs. {{MonthlyGross}}</td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;padding:3px 0;\">Annual CTC</td><td style=\"font-size:11px;font-weight:bold;text-align:right;\">Rs. {{AnnualCTC}}</td></tr>"
                + "</table>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 32px;\">This certificate is issued upon the employee's request for whatever purpose it may serve.</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"font-size:11px;\">For <b>{{CompanyName}}</b><br/><br/><br/>_____________________<br/>{{HRName}}<br/>Human Resources</td></tr></table>");

        seedTemplateIfMissing(Document.DocumentType.PAYSLIP, "Standard Payslip",
                "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td><h2 style=\"font-size:15px;margin:0;\">PAYSLIP</h2><p style=\"font-size:11px;color:#777;margin:2px 0 0;\">{{PayPeriod}}</p></td>"
                + "<td style=\"text-align:right;font-size:11px;color:#777;\">Generated: {{CurrentDate}}</td>"
                + "</tr></table><hr style=\"border:none;border-top:1px solid #eee;margin:14px 0;\"/>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;\">"
                + "<tr><td style=\"font-size:11px;color:#666;width:25%;\">Employee</td><td style=\"font-size:11px;font-weight:bold;width:25%;\">{{EmployeeName}}</td>"
                + "<td style=\"font-size:11px;color:#666;width:25%;\">Employee ID</td><td style=\"font-size:11px;font-weight:bold;width:25%;\">{{EmployeeID}}</td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;\">Department</td><td style=\"font-size:11px;font-weight:bold;\">{{Department}}</td>"
                + "<td style=\"font-size:11px;color:#666;\">Designation</td><td style=\"font-size:11px;font-weight:bold;\">{{Designation}}</td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;\">Working Days</td><td style=\"font-size:11px;font-weight:bold;\">{{WorkingDays}}</td>"
                + "<td style=\"font-size:11px;color:#666;\">Paid Days</td><td style=\"font-size:11px;font-weight:bold;\">{{PaidDays}}</td></tr>"
                + "</table>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td width=\"50%\" style=\"vertical-align:top;padding-right:12px;\"><p style=\"font-size:12px;font-weight:bold;margin:0 0 8px;\">Earnings</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">{{EarningsRows}}"
                + "<tr><td style=\"padding:6px 0;font-size:11px;font-weight:bold;border-top:1px solid #ccc;\">Gross Earnings</td><td style=\"padding:6px 0;font-size:11px;font-weight:bold;text-align:right;border-top:1px solid #ccc;\">Rs. {{GrossEarnings}}</td></tr>"
                + "</table></td>"
                + "<td width=\"50%\" style=\"vertical-align:top;padding-left:12px;\"><p style=\"font-size:12px;font-weight:bold;margin:0 0 8px;\">Deductions</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">{{DeductionsRows}}"
                + "<tr><td style=\"padding:6px 0;font-size:11px;font-weight:bold;border-top:1px solid #ccc;\">Total Deductions</td><td style=\"padding:6px 0;font-size:11px;font-weight:bold;text-align:right;border-top:1px solid #ccc;\">Rs. {{TotalDeductions}}</td></tr>"
                + "</table></td>"
                + "</tr></table>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:6px;margin:20px 0 0;\"><tr>"
                + "<td style=\"padding:14px 18px;font-size:13px;font-weight:bold;\">Net Salary Payable</td>"
                + "<td style=\"padding:14px 18px;font-size:15px;font-weight:bold;text-align:right;\">Rs. {{NetSalary}}</td>"
                + "</tr></table>");

        // The remaining 13 document types use the structured block model (see BlockRenderer)
        // rather than hand-written HTML strings - this is the canonical authoring format going
        // forward; the four above stay on the legacy bodyHtml path since they already work live
        // and there's no reason to risk rewriting something that isn't broken.
        seedBlockTemplate(Document.DocumentType.APPOINTMENT_LETTER, "Standard Appointment Letter", List.of(
                heading("APPOINTMENT LETTER"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("Further to your offer of employment, we are pleased to confirm your appointment with <b>{{CompanyName}}</b> as <b>{{Designation}}</b> in the <b>{{Department}}</b> department, effective <b>{{JoiningDate}}</b>."),
                table("Appointment Details", List.of(
                        List.of("Employee ID", "{{EmployeeID}}"),
                        List.of("Designation", "{{Designation}}"),
                        List.of("Department", "{{Department}}"),
                        List.of("Date of Joining", "{{JoiningDate}}"),
                        List.of("Work Location", "{{WorkLocation}}")
                )),
                paragraph("Your employment will be governed by the Company's HR policies as communicated separately. We look forward to a long and productive association."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "Accepted by", "{{EmployeeName}}")
        ));

        seedBlockTemplate(Document.DocumentType.JOINING_LETTER, "Standard Joining Letter", List.of(
                heading("JOINING LETTER"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("Welcome to <b>{{CompanyName}}</b>! This letter confirms your joining as <b>{{Designation}}</b> in the <b>{{Department}}</b> department, effective <b>{{JoiningDate}}</b>."),
                paragraph("Please report to <b>{{ManagerName}}</b> at <b>{{WorkLocation}}</b> on your date of joining, along with all original documents requested during onboarding."),
                paragraph("We are excited to have you on board and look forward to your contribution."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.CONFIRMATION_LETTER, "Standard Confirmation Letter", List.of(
                heading("CONFIRMATION OF EMPLOYMENT"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("We are pleased to confirm that you have successfully completed your probation period and your employment with <b>{{CompanyName}}</b> as <b>{{Designation}}</b> in the <b>{{Department}}</b> department stands confirmed with effect from <b>{{CurrentDate}}</b>."),
                paragraph("All other terms and conditions of your employment remain unchanged. Congratulations, and we look forward to your continued contribution."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.PROMOTION_LETTER, "Standard Promotion Letter", List.of(
                heading("PROMOTION LETTER"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("We are delighted to inform you that, in recognition of your performance and contribution, you have been promoted from <b>{{Designation}}</b> to <b>{{NewDesignation}}</b>, effective <b>{{EffectiveDate}}</b>."),
                table("Promotion Details", List.of(
                        List.of("Previous Designation", "{{Designation}}"),
                        List.of("New Designation", "{{NewDesignation}}"),
                        List.of("Effective Date", "{{EffectiveDate}}")
                )),
                paragraph("Congratulations on this well-deserved promotion. We look forward to your continued success at {{CompanyName}}."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.SALARY_REVISION_LETTER, "Standard Salary Revision Letter", List.of(
                heading("SALARY REVISION LETTER"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("We are pleased to inform you that your compensation has been revised, effective <b>{{EffectiveDate}}</b>, in recognition of your performance and contribution to <b>{{CompanyName}}</b>."),
                table("Revised Compensation", List.of(
                        List.of("Previous Annual CTC", "Rs. {{OldSalary}}"),
                        List.of("Revised Annual CTC", "Rs. {{NewSalary}}"),
                        List.of("Effective Date", "{{EffectiveDate}}")
                )),
                paragraph("Congratulations, and thank you for your continued dedication."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.RESIGNATION_ACCEPTANCE_LETTER, "Standard Resignation Acceptance Letter", List.of(
                heading("RESIGNATION ACCEPTANCE LETTER"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("This letter is to formally acknowledge and accept your resignation from the position of <b>{{Designation}}</b> at <b>{{CompanyName}}</b>, submitted on <b>{{ResignationDate}}</b>."),
                paragraph("Your last working day will be <b>{{LastWorkingDate}}</b>. Please coordinate with HR to complete the exit formalities, knowledge transfer, and asset handover before this date."),
                paragraph("We thank you for your contribution and wish you success in your future endeavors."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.TERMINATION_LETTER, "Standard Termination Letter", List.of(
                heading("TERMINATION OF EMPLOYMENT"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("This letter is to inform you that your employment with <b>{{CompanyName}}</b> as <b>{{Designation}}</b> is terminated with effect from <b>{{TerminationDate}}</b>, for the following reason: <b>{{Reason}}</b>."),
                paragraph("Please contact HR regarding the return of company property and settlement of dues as per policy."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.WARNING_LETTER, "Standard Warning Letter", List.of(
                heading("LETTER OF WARNING"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("This letter serves as a formal warning regarding the following matter: <b>{{Reason}}</b>."),
                paragraph("You are advised to improve your conduct/performance immediately. Failure to do so may result in further disciplinary action, up to and including termination of employment."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.INTERNSHIP_LETTER, "Standard Internship Letter", List.of(
                heading("INTERNSHIP OFFER LETTER"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("We are pleased to offer you an internship at <b>{{CompanyName}}</b> as <b>{{Designation}}</b> in the <b>{{Department}}</b> department, for a duration of <b>{{InternshipDuration}}</b> commencing <b>{{JoiningDate}}</b>."),
                paragraph("This is a training engagement and does not constitute an offer of permanent employment. Performance during the internship may be considered for a future full-time role."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "Accepted by", "{{EmployeeName}}")
        ));

        seedBlockTemplate(Document.DocumentType.CONTRACT_LETTER, "Standard Contract Letter", List.of(
                heading("CONTRACT OF EMPLOYMENT"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("This confirms your engagement with <b>{{CompanyName}}</b> as <b>{{Designation}}</b> on a contract basis for a term of <b>{{ContractDuration}}</b>, commencing <b>{{JoiningDate}}</b>."),
                table("Contract Terms", List.of(
                        List.of("Designation", "{{Designation}}"),
                        List.of("Contract Duration", "{{ContractDuration}}"),
                        List.of("Start Date", "{{JoiningDate}}")
                )),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "Accepted by", "{{EmployeeName}}")
        ));

        seedBlockTemplate(Document.DocumentType.LEAVE_APPROVAL_LETTER, "Standard Leave Approval Letter", List.of(
                heading("LEAVE APPROVAL"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("Your leave request has been <b>approved</b> as per the details below."),
                table("Leave Details", List.of(
                        List.of("Leave Type", "{{LeaveType}}"),
                        List.of("From", "{{LeaveStartDate}}"),
                        List.of("To", "{{LeaveEndDate}}"),
                        List.of("Approved By", "{{ManagerName}}")
                )),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.LEAVE_REJECTION_LETTER, "Standard Leave Rejection Letter", List.of(
                heading("LEAVE REQUEST - NOT APPROVED"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("Dear {{EmployeeName}},"),
                paragraph("Your leave request below has not been approved, for the following reason: <b>{{Reason}}</b>."),
                table("Leave Details", List.of(
                        List.of("Leave Type", "{{LeaveType}}"),
                        List.of("From", "{{LeaveStartDate}}"),
                        List.of("To", "{{LeaveEndDate}}")
                )),
                paragraph("Please reach out to your reporting manager or HR if you would like to discuss this further."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));

        seedBlockTemplate(Document.DocumentType.EMPLOYMENT_VERIFICATION_LETTER, "Standard Employment Verification Letter", List.of(
                heading("EMPLOYMENT VERIFICATION LETTER"),
                paragraph("Date: {{CurrentDate}}"),
                paragraph("To Whomsoever It May Concern,"),
                paragraph("This is to verify that <b>{{EmployeeName}}</b> (Employee ID: <b>{{EmployeeID}}</b>) is/was employed with <b>{{CompanyName}}</b> as <b>{{Designation}}</b> in the <b>{{Department}}</b> department since <b>{{JoiningDate}}</b>."),
                paragraph("This letter is issued upon request for whatever purpose it may serve."),
                signature("For {{CompanyName}}", "{{HRName}}<br/>Human Resources", "", "")
        ));
    }

    private Map<String, Object> heading(String text) {
        return Map.of("type", "heading", "text", text);
    }

    private Map<String, Object> paragraph(String text) {
        return Map.of("type", "paragraph", "text", text);
    }

    private Map<String, Object> table(String title, List<List<String>> rows) {
        return Map.of("type", "table", "title", title, "rows", rows);
    }

    private Map<String, Object> signature(String leftLabel, String leftName, String rightLabel, String rightName) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", "signatureBlock");
        m.put("leftLabel", leftLabel);
        m.put("leftName", leftName);
        m.put("rightLabel", rightLabel);
        m.put("rightName", rightName);
        return m;
    }

    // signatureRole ("CEO"/"HR"/"FINANCE") is optional - when set, BlockRenderer renders the
    // matching signature image from Company Branding above the name/underline.
    private Map<String, Object> signatureWithRole(String leftLabel, String leftName, String leftRole,
                                                    String rightLabel, String rightName, String rightRole) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", "signatureBlock");
        m.put("leftLabel", leftLabel);
        m.put("leftName", leftName);
        m.put("leftSignatureRole", leftRole);
        m.put("rightLabel", rightLabel);
        m.put("rightName", rightName);
        m.put("rightSignatureRole", rightRole);
        return m;
    }

    private Map<String, Object> list(List<String> items) {
        return Map.of("type", "list", "items", items);
    }

    private Map<String, Object> pageBreak() {
        return Map.of("type", "pageBreak");
    }

    private void seedBlockTemplate(Document.DocumentType type, String name, List<Map<String, Object>> blocks) {
        if (documentTemplateRepository.existsByDocumentType(type)) return;
        try {
            String json = objectMapper.writeValueAsString(blocks);
            documentTemplateRepository.save(DocumentTemplate.builder()
                    .documentType(type)
                    .templateGroupId(java.util.UUID.randomUUID().toString())
                    .name(name)
                    .version(1)
                    .status(DocumentTemplate.TemplateStatus.PUBLISHED)
                    .contentBlocksJson(json)
                    .createdByEmail("system-seed")
                    .build());
        } catch (Exception e) {
            log.warn("Could not seed block template for {}: {}", type, e.getMessage());
        }
    }

    // The real, multi-page Vikisol Technologies Offer Letter (Senior Business Development Manager
    // style corporate offer) - the previous state of the world had this content hardcoded, ad hoc,
    // inside EmailService.buildOfferLetterPdfHtml, entirely bypassing the template engine (no
    // branding, no versioning, no Document Studio visibility). This is now just data, authored
    // through the same createDraft()/publish() flow as any admin-created template.
    //
    // Note on the two "CONFIDENTIALITY" / "CONFLICT OF INTEREST" sections below: the source
    // document numbers them "3." and "4." a second time (after "3.PERFORMANCE AND APPRAISALS" and
    // "4.SEPARATION OF EMPLOYMENT" already used those numbers) - that duplicate numbering is
    // reproduced verbatim/intentionally, not a typo introduced here.
    // Public so AdminTemplateSeedController can trigger this deliberately in production (see the
    // profile guard in run() above) - same idempotent existsByDocumentType guard applies either way.
    public void seedOfferLetterTemplate() {
        if (documentTemplateRepository.existsByDocumentType(Document.DocumentType.OFFER_LETTER)) return;
        try {
            List<Map<String, Object>> blocks = new java.util.ArrayList<>();

            // ── Page 1: the offer itself ──────────────────────────────────────────────
            blocks.add(paragraph("{{OfferDate}}"));
            blocks.add(paragraph("Dear {{Salutation}} {{EmployeeName}},"));
            blocks.add(paragraph("Congratulations! We are pleased to confirm that you have been selected to work for Vikisol Technologies Pvt Ltd."));
            blocks.add(paragraph("With reference to your application and the discussions we have had with you, we are pleased to appoint you as a \"{{Designation}}\" at the Vikisol Technologies Pvt Ltd, India on a base salary of RS.{{MonthlySalary}} /- per month plus other perquisites. You will be based in {{Location}}."));
            blocks.add(paragraph("This position reports to {{ReportingManagerTitle}}, {{ReportingManagerName}}. Your working hours will be from [{{WorkStartTime}} to {{WorkEndTime}}], {{WorkDays}}."));
            blocks.add(paragraph("Benefits for the position include:"));
            blocks.add(list(List.of(
                    "Annexure A details our comprehensive leave policies.",
                    "Annexure B outlines a competitive pay structure, highlighting substantial position benefits."
            )));
            blocks.add(paragraph("We would like you to start work on {{JoiningDate}} at {{JoiningTime}}. Please report to, {{OrientationContact}}, for documentation and orientation. If this date is not acceptable, please contact to the organization mail ID immediately."));
            blocks.add(paragraph("Please sign the enclosed copy of this letter and return it to me by {{AcceptanceDeadline}} to indicate your acceptance of this offer."));
            blocks.add(paragraph("We are confident you will be able to make a significant contribution to the success of our Vikisol Technologies Pvt Ltd and look forward to working with you."));

            // ── Page 2+: terms and conditions ─────────────────────────────────────────
            blocks.add(pageBreak());
            blocks.add(heading("Other Terms as follows:"));
            blocks.add(heading("1.TERMS AND CONDITIONS"));
            blocks.add(paragraph("(a) The Employee shall be paid his salary on the date specified in the Annexure-I of this Employment. Vikisol Technologies shall not be responsible for any delays in payment of salary to the Employee caused by his or her late submission of attendance."));
            blocks.add(paragraph("(b) The Employee understands that it can deployed or instructed at any time to be transferred anywhere in India at any office/ premises of Vikisol Technologies and/or its concerned Client or at any office of the affiliate/associate member/ customer of the Client. The Employee further understands that failure by the Employee to accept and comply with any such transfer instruction/ request shall be sufficient grounds for termination of employment of the Employee by Vikisol Technologies."));
            blocks.add(paragraph("(c) The Employee shall be governed by the rules and regulations regarding public holidays, timings, reporting structures, working hours, leave entitlement, discipline, security requirements, work ethics, targets etc. of the concerned Client and the location of such Vikisol Technologies Client (details of which are provided in Annexure-A(i)) where the Employee is deputed."));
            blocks.add(paragraph("(d) The Employee shall not claim amount more than total salary including benefits, if any, other than those mentioned here in this Employment, unless revised, payable and communicated in writing to the Employee. The Employee understands that other than the amounts mentioned under Annexure-I it is not entitled to any other compensation or make any claims for any other amounts."));
            blocks.add(paragraph("(e) The Employee agrees in writing to protect the confidentiality of the proprietary and/ or confidential information of both Vikisol Technologies and of the Client."));
            blocks.add(paragraph("(f) The Employee shall execute any undertaking/ Employment provided by Vikisol Technologies that the Client may request of him/ her with regard to the maintenance of confidentiality of the intellectual property developed by the Employee or any work done by the Employee under the instructions of the Client during its deployment."));
            blocks.add(paragraph("(g) The Employees acknowledges that they have no right to participate in Client's employee benefit plans (unless if specifically requested or permitted by the Client which shall be informed to the Employee by Vikisol Technologies)."));
            blocks.add(paragraph("(h) For the issuance of any notice or communication of whatsoever kind, the Employee will be informed by email to personal email address/hand delivery/ courier/ registered post/ speed post or ordinary post at the address mentioned in the recitals of the Employment & in Annexure-I. In case of any change in the Employee's address or surname after marriage or any other change, the Employee will inform the concerned officials of Vikisol Technologies in writing to this effect within one (1) week of such change and get new address recorded in the Employee's personal record."));
            blocks.add(paragraph("(i) The Employee understands & agrees that this Employment and the offer of employment by Vikisol Technologies to Employee is based on the foundation of the declaration provided by Employees in respect of the information/ details provided by Employee to Vikisol Technologies in his/her c.v./ resume which is taken and believed by Vikisol Technologies to be accurate as correct especially the information pertaining to age, educational qualifications, work experience, marital status & previous employment."));
            blocks.add(paragraph("(j) In the event of any discovery/ information (by means of background check/ verification or otherwise) is made available or known to Vikisol Technologies with reference to any fraud, incorrect particulars/ statements, misinformation or suppression of any detail/ material fact on any account leading to the mistaken offer of employment having been made/acted upon by Vikisol Technologies, then this Employment shall stand automatically terminated with/without any reference/notice to the Employee with retrospective effect from the date of offer and the Employee shall be liable to make good all losses, expenses, damages caused to Vikisol Technologies on account of such acts or commissions as mentioned herein."));
            blocks.add(paragraph("(k) The terms of this Employment and appointment of Employee shall be governed by the laws of India (including the Contract Labor Regulation & Abolition Act, 1970 & Rules 1971) and shall be co-terminus with terms of the Service appointment."));

            blocks.add(heading("2.DUTIES"));
            blocks.add(paragraph("The duties and responsibilities of the Employee may be changed or altered at any time by Vikisol Technologies at its sole discretion and the Employee agrees to abide by such altered or new duties and responsibilities. The Employee shall be duly informed of these changes by Vikisol Technologies. The Employee shall be committed to the work and meet the expectation of Vikisol Technologies and its Client. The Employee shall maintain high level of integrity, acumen and discipline in the work assigned to him/her by the Client. Under/ below-par performance shall invite necessary action against the Employee (including but not limited to issuance of warning letters/ notices or termination in repeated cases of under/ below-par performance). Employee shall provide all information (personal or otherwise) as may be required by Vikisol Technologies."));

            blocks.add(heading("3.PERFORMANCE AND APPRAISALS"));
            blocks.add(paragraph("The Employee shall endeavor to perform his or her duties efficiently and to the best of his or her ability. The appraisal/increment of the Employee depends on his/her performance and on other miscellaneous factors. The Employee may be called upon to undergo any training to upgrade himself/ herself to meet the requirements of the Client/Vikisol Technologies and failure to undergo/ complete such training or fulfil the requirements of such training may render the Employee unfit for continuation of its employment with Vikisol Technologies"));

            blocks.add(heading("4.SEPARATION OF EMPLOYMENT"));
            blocks.add(paragraph("(a) Vikisol Technologies reserves the right to terminate the employment of the Employee and this Employment at any time by giving Thirty (30) days' notice to the Employee or payment of salary/ wages amount in lieu of such notice period after completion of probationary period. During probationary period employee need to serve 30 days of notice period or salary will be of lieu. An employee serving his notice period is not eligible for any leave. In case the employee avails leave due to emergencies, the notice period gets extended to the extent of leave taken"));
            blocks.add(paragraph("(b) In the event that the Employee decides to terminate his or her employment under this Employment with Vikisol Technologies, the Employee shall be required to give notice of 30 days in writing or payment of salary/ wages amount in lieu of such notice period. Full and Final settlement will take up to 45 days from the date of relieving, after collection of no due's certification and other formalities. Salary for due month will be part of Full and Final Settlement. An employee serving his notice period is not eligible for any leave. In case the employee avails leave due to emergencies, the notice period gets extended to the extent of leave taken"));
            blocks.add(paragraph("(c) Subject to the laws of India (including any state specific laws), Vikisol Technologies reserves its right to terminate this Employment immediately without notice or without payment in lieu of notice in cases of (including but not limited to) neglect of duty, misconduct, drinking alcohol on duty, coming to office in a state of intoxication or under the influence of alcohol/ drugs/ recreational substances, drinking alcohol in office premises after duty, act of fraud, conduct not beneficial to the interests of Vikisol Technologies or the Client, absent or absconding from work or extension of leaves without approval/justifiable reasons, a breach of the terms and conditions of this Employment, a breach of the rules/ regulations/code of conduct, commission of any offence punishable under Indian Penal Code or any other law applicable in India."));
            blocks.add(paragraph("(d) Deemed resignation: In case the Employee is absent from work for more than Seven (7) continuous working days without prior approval or justifiable reasons, the Employee shall be deemed to be absconding from duty and will be terminated from its employment with Vikisol Technologies. If any dues, will be forfeited from Vikisol Technologies."));
            blocks.add(paragraph("(e) Leave without notice will be treated as Loss of pay"));

            // Verbatim from the company's actual offer letter PDFs (Annexure/T&C boilerplate),
            // not paraphrased. Numbering ("3."/"4.") duplicated intentionally to match the source
            // document's own numbering quirk (it reuses "3." and "4." after PERFORMANCE AND
            // APPRAISALS / SEPARATION OF EMPLOYMENT already used those numbers).
            blocks.add(heading("3.CONFIDENTIALITY"));
            blocks.add(paragraph("(a) The Employee must keep confidential all trade secrets and information which comes to his or her attention in circumstances where he or she know or ought to know that the information is to be treated as confidential."));
            blocks.add(paragraph("(b) Confidential information includes:"));
            blocks.add(list(List.of(
                    "i, Technical information, plans and product specifications.",
                    "ii, Employee records.",
                    "iii, Business plans and forecasts.",
                    "iv. Financial records, reports, accounts, and proposals.",
                    "v. Vikisol Technologies/Client's intellectual property;",
                    "vi. Clients lists, names of Client contacts and terms of trade with Client of Vikisol Technologies",
                    "vii. Information on Client's suppliers or the Client customers or data Client would consider commercially valuable and/or secret of Vikisol Technologies",
                    "viii. Telephone lists, policy documents, training documents, quality documents and any other Internally used information regarding the operations of the Client/Vikisol Technologies.",
                    "ix. Employee's salary details and this Employment terms of Vikisol Technologies."
            )));
            blocks.add(paragraph("(c) The Employee must not remove information or copies of information from the Client's premises except where the Employee's employment specifically requires the same and/or where the Client has given written consent to Vikisol Technologies. The obligation of confidentiality exists both during the employment and after the employment ceases. Any breach of confidentiality shall be regarded as a serious misconduct for which the Employee may be dismissed or terminated forthwith without any notice or payment in lieu of notice. On the termination of the Employment, all papers, records and documents in the Employee's possession shall be returned to the Vikisol Technologies., and any other Information, documentation, record, photographs, designs, processes, systems, maps and installations which are deemed confidential by virtue of operations/ exclusive usage by Vikisol Technologies and leakage of the same to any unauthorized person, company, firm, organization etc. is detrimental to the interest of Vikisol Technologies."));
            blocks.add(paragraph("(d) The Employee shall be duty bound to return all the property, data, information, record of the Vikisol Technologies and Client (confidential/ otherwise) while leaving/ ending employment and non-return of the same will amount of breach of confidentiality and render the Employee liable for legal action except for any saving available under the laws of India."));

            blocks.add(heading("4.CONFLICT OF INTEREST"));
            blocks.add(paragraph("(a) The Employee shall not, during the validity of this Employment (except with the knowledge and written consent of both the Client and Vikisol Technologies) engage themselves whether for reward or not, in any activity which may constitute a conflict of interest with the business of Vikisol Technologies. Conflict of interest will include any instances of the Employee while being under the employment of Vikisol Technologies also getting into any separate/independent arrangement with any third party (either by making use of employment with Vikisol Technologies, deployment & work duties with Client or otherwise) and drawing amounts of profit from such third party or holding an office of profit (i.e. dual employment) with such third party."));
            blocks.add(paragraph("(b) The Employee shall not solicit or explore employment with the Client and/or any other organization/ third-party during the Employment period as mentioned in Annexure-I(including extended period, if any) and if found doing so, the same would constitute conflict of interest and render the Employee liable for legal action which may be termination and includes recovery for the loss and damages caused to Vikisol Technologies."));
            blocks.add(paragraph("(c) In case the Employee is found indulged in any conduct, behavior and activity (as mentioned in this clause or anywhere else in the Employment or otherwise) either in a group or individually which is deemed to be against the interests of the Client and/ or Vikisol Technologies or which violates the terms of this Employment, then the same would constitute conflict of interest and render the Employee liable for legal action including termination of employment without notice or without payment in lieu of notice. Additionally, Vikisol Technologies and/ or the Client is also entitled to recover the loss or damages caused to Vikisol Technologies or the Client by such conduct/ actions of the Employee."));

            blocks.add(heading("5.GOVERNING LAW & ASSENT TO ARBITRATION"));
            blocks.add(paragraph("(a) This Employment shall always be governed by the laws of India (including state specific laws or rules) and all disputes shall be subject to jurisdiction of the courts in Bangalore, India."));
            blocks.add(paragraph("(b) In case of any dispute regarding interpretation of the terms of this Employment whether during or after the period of this Employment , Vikisol Technologies upon receiving the point(s) of dispute shall upon being satisfied upon the existence of the same refer the same to an arbitrator who will be independent person and who upon his assuming charge after appointment, call both parties involved, to enquire, to investigate, hold appropriate proceedings and give his findings by way of an award as per the provisions of Arbitration and Conciliation Act. 1996 and amendments made thereafter. The award of the arbitrator shall be final and binding."));

            blocks.add(heading("6.CODE OF CONDUCT"));
            blocks.add(paragraph("While rendering services under this Employment, Employee shall ensure to conform to the highest level of professional standards and business ethics and shall abide by all the policies, processes, procedures, norms, rules and regulation of Vikisol Technologies or its Client. Indulgence in a behavior/conduct which may be prejudicial to the interests of Vikisol Technologies or its Client may warrant strict disciplinary action including but not limited to termination of Employment in accordance with clause 4 above."));

            blocks.add(heading("7.ADHERENCE TO IT POLICY"));
            blocks.add(paragraph("The Employee shall be responsible to follow the laid down IT policy of Vikisol Technologies and/ or its Client. The Employee will exercise due diligence and follow the correct laid down operating procedure while using all the hardware including Employee desktop/ laptop, printer, scanner, photo copier and any other electronic or non-electronic equipment provided to Employee. The Employee will use the allotted official Email ID for official purpose and official communication only and shall never transmit/communicate any text, message or communication in any form which may be classified as derogatory, defamatory, leading to harassment or sexual abuse to the Employee colleagues, sub-ordinates, seniors or any person having business interest in Vikisol Technologies or the Client or otherwise. The Employee shall also be responsible for the safety and security of the data including but not limited to various software installed/copied in the Employee allotted desktop/laptop or other electronic device for the period while such data/ hardware/ software is in Employee possession. The Employee shall return all the allotted data/ hardware/ software and other peripherals as the case may be to the Employee's supervisor, reporting manager immediately upon cessation of the Employee's employment with Vikisol Technologies and/ or upon end of deployment/ assignment with the Client. In case of any breach of this Employment and/ or breach of this clause in particular, Vikisol Technologies shall have exclusive right to withhold Employee's full & final settlement and issuance of relieving letter without prejudice to other rights and remedies available to them under and subject to the laws of India in force for the time being. The Employee shall also keep Vikisol Technologies and its Client indemnified against any loss or damage which they may incur due to any act of the Employee misconduct or mishandling of the said hardware and or peripherals during the term of this Employment."));

            blocks.add(heading("8.PROBATIONARY PERIOD:"));
            blocks.add(paragraph("Probationary Period 6 Months from the date of appointment (On successful completion of probationary period the employee shall receive a written confirmation). During the probation period, Management at its discretion terminate the services of the probationer's, During the original or extended period of probation, with 30 days' notice and employee will be free to leave from the service of the Vikisol Technologies with 30 days' notice or salary in lieu thereof"));

            blocks.add(heading("9. ACCEPTANCE OF THE ABOVE TERMS"));
            blocks.add(paragraph("The above terms and conditions (and those present in Annexure-I) are accepted by the Employer and Employee and shall be binding on them unless modified or altered in writing or by operation of any law and not otherwise. This Agreement (including Annexure-I) constitutes & governs entire understanding between Vikisol Technologies and the Employee to the exclusion of all other written or verbal representations, statements, understandings, negotiations or proposals and shall apply to employment relationship between the parties unless anything to the contrary is mutually agreed in writing, there by agreeing to continue \"One Year Service\" to the company without fail, unless & until the actions are taken from the Company."));

            // ── Signatures ─────────────────────────────────────────────────────────────
            blocks.add(pageBreak());
            blocks.add(paragraph("IN WITNESS WHEREOF, the parties hereby sign & execute this Agreement on the day, month and year mentioned above for & on behalf of Vikisol Technologies Pvt Ltd."));
            blocks.add(signatureWithRole("For Vikisol Technologies Pvt Ltd", "{{CeoName}}<br/>Chief Executive Officer (CEO)", "CEO", "", "", ""));
            blocks.add(paragraph("I CONFIRM THAT I HAVE CAREFULLY READ THROUGH AND UNDERSTOOD ALL THE ABOVE TERMS AND CONDITIONS OF THIS APPOINTMENT AND I UNDERTAKE TO ABIDE BY THE SAID TERMS AND CONDITIONS."));
            blocks.add(paragraph("Accepted by Name: {{EmployeeName}}"));
            blocks.add(paragraph("(Signature of Employee): ___________ Date: ___________"));

            // ── Annexure A ────────────────────────────────────────────────────────────
            blocks.add(pageBreak());
            blocks.add(heading("Miscellaneous - Annexure A"));
            blocks.add(paragraph("(i) (a) Vikisol Technologies will make a PF/ ESI and other statutory contributions as per the applicable laws of India."));
            blocks.add(paragraph("(b) Payment date of salary: On last working day of the month or before 3rd of every month immediately succeeding the month for which salary is being paid."));
            blocks.add(paragraph("(c) Leave Entitlement: As per applicable laws of India and Leave Policy as defined. 20 days own leave - needs to be Informed at least 7 days in advance with line manager as listed below: (14 Earned Leaves ,6 Casual and 3 Sick Leaves) Earned leave can be carry forward for next year and can be encashed (Encashment of the same is on Basic Salary). You will be eligible for Earned and Casual leave after Probationary period of 6 months"));

            // ── Annexure B ────────────────────────────────────────────────────────────
            blocks.add(pageBreak());
            blocks.add(heading("Annexure B"));
            blocks.add(annexureBTable());

            String json = objectMapper.writeValueAsString(blocks);
            var request = new DocumentTemplateRequest(Document.DocumentType.OFFER_LETTER, "Corporate Offer Letter", json, null);
            var draft = documentTemplateService.createDraft(request, "system-seed");
            documentTemplateService.publish(draft.id());
            log.info("Seeded and published Corporate Offer Letter template ({} content blocks)", blocks.size());
        } catch (Exception e) {
            log.warn("Could not seed Offer Letter template: {}", e.getMessage());
        }
    }

    // Matches the real Annexure B layout (Fixed Components / Benefits / Deferrals / Total CTC).
    // PT is sourced from PayrollService.getConfigAsBigDecimal("PROFESSIONAL_TAX") (a real system
    // config value, not fabricated) since computeCtcBreakup() doesn't return it directly. PF and
    // Medical(insurance) rows are left as "-" (matching the source PDFs, which show "-" for those
    // rows too since this company doesn't currently deduct them at offer stage).
    private Map<String, Object> annexureBTable() {
        java.util.Map<String, Object> t = new java.util.LinkedHashMap<>();
        t.put("type", "table");
        t.put("columns", List.of("Fixed Components", "CTC per Month", "CTC Per Annum"));
        t.put("rows", List.of(
                List.of("Basic", "{{BasicMonthly}}", "{{BasicAnnual}}"),
                List.of("House Rent Allowance", "{{HRAMonthly}}", "{{HRAAnnual}}"),
                List.of("Special Allowance", "{{SpecialAllowanceMonthly}}", "{{SpecialAllowanceAnnual}}"),
                List.of("Other Allowance", "{{OtherAllowanceMonthly}}", "{{OtherAllowanceAnnual}}"),
                List.of("Total Fixed Salary", "{{TotalFixedMonthly}}", "{{TotalFixedAnnual}}"),
                List.of("Benefits", "-", "-"),
                List.of("PT", "{{PTMonthly}}", "{{PTAnnual}}"),
                List.of("PF", "-", "-"),
                List.of("Medical(insurance)", "-", "-"),
                List.of("Total Deferrals", "{{PTMonthly}}", "{{PTAnnual}}"),
                List.of("Total CTC (A + B)", "{{TotalCtcMonthly}}", "{{TotalCtcAnnual}}")
        ));
        return t;
    }

    private void seedTemplateIfMissing(Document.DocumentType type, String name, String bodyHtml) {
        if (documentTemplateRepository.existsByDocumentType(type)) return;
        documentTemplateRepository.save(DocumentTemplate.builder()
                .documentType(type)
                .templateGroupId(java.util.UUID.randomUUID().toString())
                .name(name)
                .version(1)
                .status(DocumentTemplate.TemplateStatus.PUBLISHED)
                .bodyHtml(bodyHtml)
                .createdByEmail("system-seed")
                .build());
    }

    // The starter set of global placeholders every document type can use - admins can add more
    // (global or type-scoped) via POST /template-variables without any code change.
    private void seedTemplateVariables() {
        seedVariable("EmployeeName", "Employee Full Name", "Full name of the employee", null);
        seedVariable("EmployeeID", "Employee ID", "System-assigned employee code (e.g. VIK-0007)", null);
        seedVariable("Designation", "Designation", "Employee's job title", null);
        seedVariable("Department", "Department", "Employee's department", null);
        seedVariable("JoiningDate", "Joining Date", "Date of joining", null);
        seedVariable("Salary", "Annual CTC", "Employee's annual CTC", null);
        seedVariable("WorkLocation", "Work Location", "Employee's city/work location", null);
        seedVariable("ManagerName", "Reporting Manager Name", "Name of the employee's reporting manager", null);
        seedVariable("CompanyName", "Company Name", "Company legal name (from Branding settings)", null);
        seedVariable("CompanyAddress", "Company Address", "Company registered address (from Branding settings)", null);
        seedVariable("Website", "Company Website", "From Branding settings", null);
        seedVariable("CeoName", "CEO Name", "From Branding settings", null);
        seedVariable("HRName", "HR Name", "From Branding settings", null);
        seedVariable("CurrentDate", "Current Date", "Today's date, auto-filled at generation time", null);

        // Offer-Letter-specific placeholders (see DataSeeder.seedOfferLetterTemplate) - scoped to
        // OFFER_LETTER since they don't make sense on other document types.
        var offerType = Document.DocumentType.OFFER_LETTER;
        seedVariable("OfferDate", "Offer Date", "Date the offer letter is issued", offerType);
        seedVariable("Salutation", "Salutation", "Mr./Ms./Mx. etc.", offerType);
        seedVariable("MonthlySalary", "Monthly Salary", "Base salary per month", offerType);
        seedVariable("Location", "Work Location", "City/office the candidate is based in", offerType);
        seedVariable("ReportingManagerTitle", "Reporting Manager Title", "Job title of the reporting manager", offerType);
        seedVariable("ReportingManagerName", "Reporting Manager Name", "Name of the reporting manager", offerType);
        seedVariable("WorkStartTime", "Work Start Time", "Daily working hours start", offerType);
        seedVariable("WorkEndTime", "Work End Time", "Daily working hours end", offerType);
        seedVariable("WorkDays", "Work Days", "Working days description (e.g. Monday to Friday)", offerType);
        seedVariable("JoiningTime", "Joining Time", "Time of day to report on the joining date", offerType);
        seedVariable("OrientationContact", "Orientation Contact", "Person to report to for documentation/orientation", offerType);
        seedVariable("AcceptanceDeadline", "Acceptance Deadline", "Date the signed offer must be returned by", offerType);
        seedVariable("BasicMonthly", "Basic (Monthly)", "Monthly basic salary component", offerType);
        seedVariable("BasicAnnual", "Basic (Annual)", "Annual basic salary component", offerType);
        seedVariable("HRAMonthly", "HRA (Monthly)", "Monthly house rent allowance", offerType);
        seedVariable("HRAAnnual", "HRA (Annual)", "Annual house rent allowance", offerType);
        seedVariable("ConveyanceMonthly", "Conveyance (Monthly)", "Monthly conveyance allowance", offerType);
        seedVariable("ConveyanceAnnual", "Conveyance (Annual)", "Annual conveyance allowance", offerType);
        seedVariable("MedicalMonthly", "Medical (Monthly)", "Monthly medical allowance", offerType);
        seedVariable("MedicalAnnual", "Medical (Annual)", "Annual medical allowance", offerType);
        seedVariable("SpecialAllowanceMonthly", "Special Allowance (Monthly)", "Monthly special allowance", offerType);
        seedVariable("SpecialAllowanceAnnual", "Special Allowance (Annual)", "Annual special allowance", offerType);
        seedVariable("OtherAllowanceMonthly", "Other Allowance (Monthly)", "Monthly custom/other allowance", offerType);
        seedVariable("OtherAllowanceAnnual", "Other Allowance (Annual)", "Annual custom/other allowance", offerType);
        seedVariable("TotalFixedMonthly", "Total Fixed Salary (Monthly)", "Total monthly gross/fixed salary", offerType);
        seedVariable("TotalFixedAnnual", "Total Fixed Salary (Annual)", "Total annual gross/fixed salary", offerType);
        seedVariable("TotalCtcMonthly", "Total CTC (Monthly)", "Total CTC divided monthly", offerType);
        seedVariable("TotalCtcAnnual", "Total CTC (Annual)", "Total annual CTC", offerType);
    }

    private void seedVariable(String key, String label, String description, Document.DocumentType type) {
        if (templateVariableRepository.existsByKey(key)) return;
        templateVariableRepository.save(TemplateVariable.builder()
                .key(key).label(label).description(description).documentType(type).build());
    }

    private void seedEmployeesForUsers() {
        List<User> users = userRepository.findAll();
        List<Department> depts = departmentRepository.findAll();
        List<Designation> desigs = designationRepository.findAll();
        if (depts.isEmpty() || desigs.isEmpty()) return;

        Department hrDept = depts.stream().filter(d -> d.getCode().equals("HR")).findFirst().orElse(depts.get(0));
        Department engDept = depts.stream().filter(d -> d.getCode().equals("ENG")).findFirst().orElse(depts.get(0));
        Department finDept = depts.stream().filter(d -> d.getCode().equals("FIN")).findFirst().orElse(depts.get(0));

        Designation ceoDesig = desigs.stream().filter(d -> d.getTitle().contains("CEO")).findFirst().orElse(desigs.get(0));
        Designation hrMgrDesig = desigs.stream().filter(d -> d.getTitle().contains("HR Manager")).findFirst().orElse(desigs.get(1));
        Designation mgrDesig = desigs.stream().filter(d -> d.getTitle().equals("Manager")).findFirst().orElse(desigs.get(1));
        Designation engDesig = desigs.stream().filter(d -> d.getTitle().contains("Software Engineer") && !d.getTitle().contains("Senior")).findFirst().orElse(desigs.get(0));
        Designation finMgrDesig = desigs.stream().filter(d -> d.getTitle().contains("Finance Manager")).findFirst().orElse(desigs.get(1));
        Designation recruiterDesig = desigs.stream().filter(d -> d.getTitle().contains("Recruiter")).findFirst().orElse(desigs.get(0));
        Designation adminDesig = desigs.stream().filter(d -> d.getTitle().contains("Manager")).findFirst().orElse(desigs.get(0));

        // Start counter after the highest existing employee number
        int maxNum = employeeRepository.findAll().stream()
                .map(e -> e.getEmployeeId())
                .filter(id -> id != null && id.startsWith("VIK-"))
                .mapToInt(id -> { try { return Integer.parseInt(id.substring(4)); } catch (Exception e2) { return 0; } })
                .max().orElse(0);
        int[] counter = {maxNum + 1};
        for (User user : users) {
            if (employeeRepository.findByUserId(user.getId()).isPresent()) continue;
            Department dept;
            Designation desig;
            BigDecimal basic;
            switch (user.getRole()) {
                case CEO -> { dept = engDept; desig = ceoDesig; basic = new BigDecimal("250000"); }
                case HR_MANAGER -> { dept = hrDept; desig = hrMgrDesig; basic = new BigDecimal("80000"); }
                case MANAGER -> { dept = engDept; desig = mgrDesig; basic = new BigDecimal("75000"); }
                case FINANCE -> { dept = finDept; desig = finMgrDesig; basic = new BigDecimal("70000"); }
                case RECRUITER -> { dept = hrDept; desig = recruiterDesig; basic = new BigDecimal("55000"); }
                case ADMIN -> { dept = engDept; desig = adminDesig; basic = new BigDecimal("60000"); }
                default -> { dept = engDept; desig = engDesig; basic = new BigDecimal("50000"); }
            }
            BigDecimal hra = basic.multiply(new BigDecimal("0.4"));
            BigDecimal gross = basic.multiply(new BigDecimal("1.6"));

            Employee emp = new Employee();
            emp.setUser(user);
            emp.setEmployeeId(String.format("VIK-%04d", counter[0]++));
            emp.setFirstName(user.getFirstName());
            emp.setLastName(user.getLastName());
            emp.setEmail(user.getEmail());
            emp.setDepartment(dept);
            emp.setDesignation(desig);
            emp.setDateOfJoining(LocalDate.of(2024, 1, 1));
            emp.setEmploymentType(Employee.EmploymentType.FULL_TIME);
            emp.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);
            emp.setCity("Hyderabad");
            emp.setBasicSalary(basic);
            emp.setHra(hra);
            emp.setGrossSalary(gross);
            emp.setCtc(gross.multiply(new BigDecimal("12")));
            emp.setActive(true);
            employeeRepository.save(emp);
        }
        log.info("  -> Employee records created for {} seeded users", users.size());
    }
}
