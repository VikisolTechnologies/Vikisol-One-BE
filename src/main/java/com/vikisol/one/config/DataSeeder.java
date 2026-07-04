package com.vikisol.one.config;

import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.one.doctemplate.entity.DocumentTemplate;
import com.vikisol.one.doctemplate.entity.TemplateVariable;
import com.vikisol.one.doctemplate.repository.DocumentTemplateRepository;
import com.vikisol.one.doctemplate.repository.TemplateVariableRepository;
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
    private final ObjectMapper objectMapper;

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
        seedDocumentTemplates();
        seedTemplateVariables();

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
