package com.vikisol.one.employee.service;

import com.vikisol.one.department.entity.Department;
import com.vikisol.one.department.repository.DepartmentRepository;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import com.vikisol.one.employee.dto.BulkImportResult;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.entity.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Separate bean (not a method on EmployeeService itself) so each row's createEmployee() call goes
// through the Spring proxy as a genuine external call, getting its own transaction - one bad row
// rolls back only that row instead of either the self-invocation bypassing @Transactional
// entirely, or one failure aborting the whole batch.
@Service
@RequiredArgsConstructor
public class EmployeeBulkImportService {

    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;

    // Expected header row (case-insensitive, any order): FullName, OfficialEmail, PersonalEmail,
    // PersonalMobile, Department, Designation, Location, EmploymentType
    // Only FullName and OfficialEmail are mandatory - everything else may be blank and filled in
    // later via Edit Employee, matching how much the Add Employee modal itself requires.
    public BulkImportResult importCsv(MultipartFile file) throws IOException {
        List<Department> departments = departmentRepository.findAll();
        List<Designation> designations = designationRepository.findAll();

        int created = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new com.vikisol.one.common.exception.BadRequestException("The uploaded file is empty");
            }
            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> colIndex = new java.util.HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
            }

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] cols = splitCsvLine(line);
                try {
                    String fullName = cell(cols, colIndex, "fullname");
                    String officialEmail = cell(cols, colIndex, "officialemail");
                    if (fullName == null || fullName.isBlank() || officialEmail == null || officialEmail.isBlank()) {
                        throw new IllegalArgumentException("FullName and OfficialEmail are required");
                    }
                    String[] nameParts = fullName.trim().split("\\s+", 2);
                    String firstName = nameParts[0];
                    String lastName = nameParts.length > 1 ? nameParts[1] : "";

                    String deptName = cell(cols, colIndex, "department");
                    java.util.UUID departmentId = deptName == null || deptName.isBlank() ? null
                            : departments.stream().filter(d -> d.getName() != null && d.getName().equalsIgnoreCase(deptName.trim()))
                                    .findFirst().map(Department::getId)
                                    .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + deptName));

                    String desigTitle = cell(cols, colIndex, "designation");
                    java.util.UUID designationId = desigTitle == null || desigTitle.isBlank() ? null
                            : designations.stream().filter(d -> d.getTitle() != null && d.getTitle().equalsIgnoreCase(desigTitle.trim()))
                                    .findFirst().map(Designation::getId)
                                    .orElseThrow(() -> new IllegalArgumentException("Unknown designation: " + desigTitle));

                    String employmentTypeStr = cell(cols, colIndex, "employmenttype");
                    Employee.EmploymentType employmentType = null;
                    if (employmentTypeStr != null && !employmentTypeStr.isBlank()) {
                        try {
                            employmentType = Employee.EmploymentType.valueOf(employmentTypeStr.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Unknown employment type: " + employmentTypeStr);
                        }
                    }

                    EmployeeRequest request = new EmployeeRequest(
                            null, firstName, lastName, officialEmail.trim(), null,                    // userId,firstName,lastName,email,phone
                            cell(cols, colIndex, "personalemail"), cell(cols, colIndex, "personalmobile"), // personalEmail,personalMobile
                            null, null, departmentId, designationId,                                  // dateOfBirth,gender,departmentId,designationId
                            null, null, null, null, employmentType, null,                              // dateOfJoining,probationEndDate,confirmationDate,reportingManagerId,employmentType,employmentStatus
                            null, null, cell(cols, colIndex, "location"), null, null, null,            // currentAddress,permanentAddress,city,state,country,pincode
                            null, null, null, null, null, null, null, null,                            // bankName,bankAccountNumber,ifscCode,panNumber,aadharNumber,uanNumber,pfNumber,esiNumber
                            null, null, null, null,                                                    // emergencyContactName,emergencyContactPhone,emergencyContactRelation,profilePictureUrl
                            null, null, null, null, null, null, null, null,                            // basicSalary,hra,conveyanceAllowance,medicalAllowance,specialAllowance,customAllowance,grossSalary,ctc
                            null, null, null, null, null, null, null, null, null);                     // nomineeName,nomineeRelation,nomineeDateOfBirth,nomineeSharePercentage,nomineeGender,maritalStatus,nationality,bloodGroup,languagesKnown

                    employeeService.createEmployee(request);
                    created++;
                } catch (Exception e) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                }
            }
        }

        return new BulkImportResult(created, errors.size(), errors);
    }

    private String cell(String[] cols, Map<String, Integer> colIndex, String key) {
        Integer idx = colIndex.get(key);
        if (idx == null || idx >= cols.length) return null;
        String v = cols[idx].trim();
        return v.isEmpty() ? null : v;
    }

    // Minimal CSV split - handles simple quoted fields containing commas, sufficient for the
    // plain export-then-reimport workflow this feature is meant for (not a general-purpose parser).
    private String[] splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }
}
