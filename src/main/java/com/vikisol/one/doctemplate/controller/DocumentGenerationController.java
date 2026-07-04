package com.vikisol.one.doctemplate.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.doctemplate.dto.DocumentGenerateRequest;
import com.vikisol.one.doctemplate.service.DocumentGenerationService;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// The generic entry point for every HR document type - this is what makes "add a new document
// type without backend code" actually true: as long as Document.DocumentType has the enum value
// and a PUBLISHED template exists, this endpoint can generate it. Type-specific dedicated
// endpoints (generate-offer-letter, payslip/{id}/generate-pdf, etc.) still exist separately
// where the caller needs to pull non-Employee data (e.g. Payslip fields) - this endpoint covers
// every document type that only needs Employee fields + a few extra caller-supplied values.
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentGenerationController {

    private final DocumentGenerationService documentGenerationService;
    private final EmployeeRepository employeeRepository;

    private static final Map<Document.DocumentType, String> TITLES = new LinkedHashMap<>() {{
        put(Document.DocumentType.OFFER_LETTER, "Offer Letter");
        put(Document.DocumentType.APPOINTMENT_LETTER, "Appointment Letter");
        put(Document.DocumentType.JOINING_LETTER, "Joining Letter");
        put(Document.DocumentType.EXPERIENCE_LETTER, "Experience Letter");
        put(Document.DocumentType.RELIEVING_LETTER, "Relieving Letter");
        put(Document.DocumentType.SALARY_CERTIFICATE, "Salary Certificate");
        put(Document.DocumentType.CONFIRMATION_LETTER, "Confirmation Letter");
        put(Document.DocumentType.PROMOTION_LETTER, "Promotion Letter");
        put(Document.DocumentType.SALARY_REVISION_LETTER, "Salary Revision Letter");
        put(Document.DocumentType.RESIGNATION_ACCEPTANCE_LETTER, "Resignation Acceptance Letter");
        put(Document.DocumentType.TERMINATION_LETTER, "Termination Letter");
        put(Document.DocumentType.WARNING_LETTER, "Warning Letter");
        put(Document.DocumentType.INTERNSHIP_LETTER, "Internship Letter");
        put(Document.DocumentType.CONTRACT_LETTER, "Contract Letter");
        put(Document.DocumentType.LEAVE_APPROVAL_LETTER, "Leave Approval Letter");
        put(Document.DocumentType.LEAVE_REJECTION_LETTER, "Leave Rejection Letter");
        put(Document.DocumentType.EMPLOYMENT_VERIFICATION_LETTER, "Employment Verification Letter");
    }};

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> generate(@RequestBody DocumentGenerateRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Map<String, String> fields = documentGenerationService.standardFieldsFor(employee,
                id -> employeeRepository.findById(id).orElse(null));
        if (request.fields() != null) fields.putAll(request.fields()); // caller-supplied values win

        String title = TITLES.getOrDefault(request.documentType(), request.documentType().name());
        String fileUrl = documentGenerationService.generateAndStore(
                request.documentType(), request.templateId(), fields, employee, title);
        return ResponseEntity.ok(new ApiResponse<>(true, title + " generated", fileUrl));
    }

    // Renders without storing/recording a Document entry - for "Preview before Download" in
    // Document Studio and the employee-facing UI alike.
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<byte[]> preview(@RequestBody DocumentGenerateRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Map<String, String> fields = documentGenerationService.standardFieldsFor(employee,
                id -> employeeRepository.findById(id).orElse(null));
        if (request.fields() != null) fields.putAll(request.fields());

        byte[] pdf = documentGenerationService.render(request.documentType(), request.templateId(), fields);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview.pdf")
                .body(pdf);
    }
}
