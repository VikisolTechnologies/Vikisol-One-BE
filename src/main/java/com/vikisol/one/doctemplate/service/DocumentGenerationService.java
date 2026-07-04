package com.vikisol.one.doctemplate.service;

import com.vikisol.one.common.service.FileModule;
import com.vikisol.one.common.service.FileStorageService;
import com.vikisol.one.common.service.PdfService;
import com.vikisol.one.doctemplate.entity.DocumentTemplate;
import com.vikisol.one.doctemplate.repository.DocumentTemplateRepository;
import com.vikisol.one.document.dto.DocumentUploadRequest;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.document.service.DocumentService;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.settings.dto.BrandingDto;
import com.vikisol.one.settings.service.BrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// The reusable document engine: every HR document (offer letter, payslip, experience letter,
// salary certificate, etc.) goes through the same path - load the active template for the
// document type, substitute {{Placeholder}} tokens, wrap in the shared branded header/footer
// chrome, render to PDF, store, and record it against the employee's Document list.
//
// Adding a brand-new document type (e.g. "Bonafide Certificate") needs: one new
// Document.DocumentType enum value, one template row created via Document Studio (POST
// /document-templates), and one small caller building the placeholder map from real data -
// no new PDF-layout Java code required.
@Service
@RequiredArgsConstructor
public class DocumentGenerationService {

    private final DocumentTemplateRepository templateRepository;
    private final BrandingService brandingService;
    private final PdfService pdfService;
    private final FileStorageService fileStorageService;
    private final DocumentService documentService;
    private final BlockRenderer blockRenderer;

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /**
     * Renders a document into a PDF. If templateId is null, uses the most recently published
     * template for the document type; otherwise renders that specific template (still requires
     * it to be PUBLISHED) - this is what lets a caller pick between e.g. "Corporate Offer Letter"
     * and "Intern Offer Letter" for the same document type.
     */
    public byte[] render(Document.DocumentType type, UUID templateId, Map<String, String> fields) {
        DocumentTemplate template = resolveTemplate(type, templateId);

        BrandingDto branding = brandingService.getBranding();
        Map<String, String> allFields = new LinkedHashMap<>();
        allFields.put("CompanyName", branding.companyName());
        allFields.put("CompanyAddress", branding.companyAddress());
        allFields.put("Website", branding.website());
        allFields.put("CeoName", branding.ceoName());
        allFields.put("HRName", branding.hrName());
        allFields.put("CurrentDate", LocalDate.now().format(DISPLAY_DATE));
        allFields.putAll(fields); // caller-supplied values win over the branding defaults above

        String templateBody = template.getContentBlocksJson() != null && !template.getContentBlocksJson().isBlank()
                ? blockRenderer.render(template.getContentBlocksJson())
                : template.getBodyHtml();
        String body = substitute(templateBody, allFields);
        String fullHtml = wrapWithChrome(body, branding);
        return pdfService.renderPdf(fullHtml);
    }

    public byte[] render(Document.DocumentType type, Map<String, String> fields) {
        return render(type, null, fields);
    }

    private DocumentTemplate resolveTemplate(Document.DocumentType type, UUID templateId) {
        if (templateId != null) {
            DocumentTemplate template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("Template not found"));
            if (template.getStatus() != DocumentTemplate.TemplateStatus.PUBLISHED) {
                throw new RuntimeException("Template '" + template.getName() + "' is not published");
            }
            return template;
        }
        return templateRepository.findFirstByDocumentTypeAndStatusOrderByVersionDesc(type, DocumentTemplate.TemplateStatus.PUBLISHED)
                .orElseThrow(() -> new RuntimeException("No published template configured for " + type + " yet - create one in Document Studio"));
    }

    /** Renders, stores in Cloudinary, and records a Document entry for the given employee. */
    public String generateAndStore(Document.DocumentType type, Map<String, String> fields, Employee employee, String documentTitle) {
        return generateAndStore(type, null, fields, employee, documentTitle);
    }

    /**
     * Standard fields resolvable from an Employee record alone - callers building any new
     * document type can start from this map and just add type-specific fields on top
     * (e.g. {"Reason": "...", "NewDesignation": "..."} for a Promotion Letter), instead of
     * re-deriving EmployeeName/Designation/Department/etc every time.
     */
    public Map<String, String> standardFieldsFor(Employee employee, java.util.function.Function<UUID, Employee> managerLookup) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("EmployeeName", employee.getFirstName() + " " + employee.getLastName());
        fields.put("EmployeeID", employee.getEmployeeId());
        fields.put("Designation", employee.getDesignation() != null ? employee.getDesignation().getTitle() : "");
        fields.put("Department", employee.getDepartment() != null ? employee.getDepartment().getName() : "");
        fields.put("JoiningDate", employee.getDateOfJoining() != null ? employee.getDateOfJoining().format(DISPLAY_DATE) : "");
        fields.put("Salary", employee.getCtc() != null ? employee.getCtc().toPlainString() : "");
        fields.put("WorkLocation", employee.getCity() != null ? employee.getCity() : "");
        if (employee.getReportingManagerId() != null && managerLookup != null) {
            Employee manager = managerLookup.apply(employee.getReportingManagerId());
            fields.put("ManagerName", manager != null ? manager.getFirstName() + " " + manager.getLastName() : "");
        }
        return fields;
    }

    public String generateAndStore(Document.DocumentType type, UUID templateId, Map<String, String> fields, Employee employee, String documentTitle) {
        byte[] pdf = render(type, templateId, fields);
        String fileName = documentTitle.replaceAll("[^a-zA-Z0-9]+", "_") + "_" + employee.getEmployeeId() + ".pdf";
        String folderSlug = type.name().toLowerCase().replace('_', '-') + "s";
        String fileUrl = fileStorageService.storeBytes(pdf, fileName, FileModule.EMPLOYEE, employee.getEmployeeId(), folderSlug);
        documentService.uploadDocument(new DocumentUploadRequest(
                employee.getId(), documentTitle, type, fileUrl, fileName, pdf.length, "application/pdf",
                "Generated via Document Studio"));
        return fileUrl;
    }

    private String substitute(String html, Map<String, String> fields) {
        String result = html;
        for (var entry : fields.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            // Fields ending in "Rows"/"Html" are pre-built markup fragments (e.g. payslip earnings
            // table rows) meant to be spliced in as real HTML, not escaped text - everything else
            // is treated as plain dynamic text and must be escaped to keep the XML strict-parsed.
            boolean isRawHtml = entry.getKey().endsWith("Rows") || entry.getKey().endsWith("Html");
            result = result.replace("{{" + entry.getKey() + "}}", isRawHtml ? value : escapeXml(value));
        }
        return result;
    }

    // openhtmltopdf parses the document as strict XML - unescaped &, <, > in dynamic values
    // (e.g. an employee name or reason text containing "&") would otherwise break rendering.
    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Shared visual chrome (dark header bar with logo + tagline, light footer bar with company
    // info) matching the look already established by the offer-letter PDF, so every document
    // type looks like it belongs to the same company regardless of which template generated it.
    private String wrapWithChrome(String bodyHtml, BrandingDto branding) {
        return "<html><head><meta charset=\"UTF-8\"/></head>"
                + "<body style=\"margin:0;padding:0;font-family:Helvetica,Arial,sans-serif;color:#1a1a1a;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"background:#0a0a0a;padding:24px 40px;\">"
                + "<img src=\"" + branding.logoUrl() + "\" alt=\"" + branding.companyName() + "\" style=\"height:34px;\"/>"
                + "<div style=\"color:#9a9a9a;font-size:9px;letter-spacing:2px;margin-top:8px;\">TECHNOLOGY &#8226; TALENT &#8226; TRANSFORMATION</div>"
                + "</td></tr></table>"
                + "<div style=\"padding:36px 40px;\">"
                + bodyHtml
                + "</div>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"background:#f4f4f5;padding:16px 40px;color:#888;font-size:9px;text-align:center;\">"
                + branding.companyName() + " &#183; " + branding.email() + " &#183; " + branding.website()
                + "</td></tr></table>"
                + "</body></html>";
    }
}
