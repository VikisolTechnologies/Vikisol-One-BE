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
                ? blockRenderer.render(template.getContentBlocksJson(), branding)
                : template.getBodyHtml();
        String body = substitute(templateBody, allFields);
        assertNoUnresolvedPlaceholders(body, template.getName());
        String fullHtml = wrapWithChrome(body, branding);
        return pdfService.renderPdf(fullHtml);
    }

    // Safety net independent of any specific caller's field-building logic: if ANY {{Token}}
    // survives substitution - a missing key, a typo in the template, a future document type
    // whose caller forgot a field - generation fails loudly with the exact missing variable(s)
    // named, instead of silently shipping a PDF with a literal "{{ManagerName}}" in it (a real
    // bug this caught: JOINING_LETTER/LEAVE_APPROVAL_LETTER for employees with no manager set).
    private void assertNoUnresolvedPlaceholders(String html, String templateName) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{([A-Za-z]+)\\}\\}").matcher(html);
        java.util.Set<String> missing = new java.util.LinkedHashSet<>();
        while (matcher.find()) missing.add(matcher.group(1));
        if (!missing.isEmpty()) {
            throw new RuntimeException("Cannot generate '" + templateName + "': missing value(s) for " + missing
                    + " - supply these fields or update the template.");
        }
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
        // Always set the key, even with no manager - a real prod bug (found via actual PDF
        // content inspection, not just an HTTP 200 check) was a literal unresolved
        // "{{ManagerName}}" left in Joining/Leave Approval letters for employees without a
        // reporting manager, because this key was previously absent from the map entirely.
        String managerName = "";
        if (employee.getReportingManagerId() != null && managerLookup != null) {
            Employee manager = managerLookup.apply(employee.getReportingManagerId());
            managerName = manager != null ? manager.getFirstName() + " " + manager.getLastName() : "";
        }
        fields.put("ManagerName", managerName);
        return fields;
    }

    // Returns a download URL with a human-readable filename (e.g.
    // "Offer_Letter_John_Doe_VIK-0007_2026-07-04.pdf") - internal Cloudinary storage still uses
    // a UUID public_id (see FileStorageService), but nobody should ever see that UUID: not in
    // the downloaded file's name, not in the browser's save dialog.
    public String generateAndStore(Document.DocumentType type, UUID templateId, Map<String, String> fields, Employee employee, String documentTitle) {
        byte[] pdf = render(type, templateId, fields);
        String employeeFullName = employee.getFirstName() + " " + employee.getLastName();
        String fileName = documentTitle.replaceAll("[^a-zA-Z0-9]+", "_") + "_" + employee.getEmployeeId() + ".pdf";
        String downloadFileName = "%s_%s_%s_%s.pdf".formatted(
                documentTitle.replaceAll("[^a-zA-Z0-9]+", "_"),
                employeeFullName.replaceAll("[^a-zA-Z0-9]+", "_"),
                employee.getEmployeeId(),
                LocalDate.now());
        String folderSlug = type.name().toLowerCase().replace('_', '-') + "s";
        String fileUrl = fileStorageService.storeBytes(pdf, fileName, FileModule.EMPLOYEE, employee.getEmployeeId(), folderSlug);
        var document = documentService.uploadDocument(new DocumentUploadRequest(
                employee.getId(), documentTitle, type, fileUrl, downloadFileName, pdf.length, "application/pdf",
                "Generated via Document Studio"));
        // Relative path - our own download proxy, not a raw Cloudinary URL (see
        // DocumentService.downloadDocument for why: Cloudinary's fl_attachment flag needs an
        // account setting we don't control, and returned a 401 when tested live).
        return document.fileUrl();
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

    // Shared visual chrome (header bar with logo, footer bar with company info) that every
    // document type inherits from the Company Branding settings - color, font, and margins are
    // no longer hardcoded here, they come from BrandingDto so a CEO/HR Admin change on the
    // Company Branding page takes effect on the next document generated, no code/redeploy needed.
    private String wrapWithChrome(String bodyHtml, BrandingDto branding) {
        String watermark = branding.watermarkUrl() != null && !branding.watermarkUrl().isBlank()
                ? "<div style=\"position:fixed;top:35%;left:15%;opacity:0.08;z-index:-1;\">"
                  + "<img src=\"" + branding.watermarkUrl() + "\" style=\"width:400px;\"/></div>"
                : "";
        String footerText = branding.footerText() != null && !branding.footerText().isBlank()
                ? branding.footerText()
                : branding.companyName() + " &#183; " + branding.email() + " &#183; " + branding.website();
        // Letterhead takes over the whole header area (full-width image, no color bar/tagline)
        // when Company Branding has one configured - that's the common case for a company that
        // already has a designed letterhead asset; templates that don't set it keep the existing
        // logo + color-bar header exactly as before (additive, not a breaking change).
        String header = branding.letterheadUrl() != null && !branding.letterheadUrl().isBlank()
                ? "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td>"
                  + "<img src=\"" + branding.letterheadUrl() + "\" alt=\"" + branding.companyName() + "\" style=\"width:100%;display:block;\"/>"
                  + "</td></tr></table>"
                : "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"background:" + branding.secondaryColor() + ";padding:24px 40px;\">"
                  + "<img src=\"" + branding.logoUrl() + "\" alt=\"" + branding.companyName() + "\" style=\"height:34px;\"/>"
                  + "<div style=\"color:#9a9a9a;font-size:9px;letter-spacing:2px;margin-top:8px;\">TECHNOLOGY &#8226; TALENT &#8226; TRANSFORMATION</div>"
                  + "</td></tr></table>";
        return "<html><head><meta charset=\"UTF-8\"/></head>"
                + "<body style=\"margin:0;padding:0;font-family:" + branding.fontFamily() + ";color:#1a1a1a;\">"
                + watermark
                + header
                + "<div style=\"padding:" + branding.defaultMargin() + ";\">"
                + bodyHtml
                + "</div>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"background:#f4f4f5;padding:16px 40px;color:#888;font-size:9px;text-align:center;\">"
                + footerText
                + "</td></tr></table>"
                + "</body></html>";
    }
}
