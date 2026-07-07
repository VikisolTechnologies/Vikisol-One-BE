package com.vikisol.one.doctemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.one.settings.dto.BrandingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// Renders a template's structured content blocks into an HTML fragment. This is the reusable
// "document model" the rest of the engine is built around, instead of one hardcoded HTML string
// per document type - the same block schema below is what a future drag-and-drop designer would
// read/write, and what a future DOCX exporter would walk instead of parsing HTML back apart.
//
// Block schema (each block is a JSON object with a "type"):
//   {"type":"heading",       "text":"OFFER OF EMPLOYMENT"}
//   {"type":"paragraph",     "text":"Dear {{EmployeeName}}, ..."}
//   {"type":"table",         "title":"Employment Terms", "rows":[["Employee ID","{{EmployeeID}}"], ...]}
//   {"type":"list",          "items":["Term one...", "Term two..."]}
//   {"type":"signatureBlock","leftLabel":"For {{CompanyName}}","leftName":"Authorized Signatory",
//                             "rightLabel":"Accepted by","rightName":"{{EmployeeName}}",
//                             "leftSignatureRole":"CEO|HR|FINANCE","rightSignatureRole":"CEO|HR|FINANCE"}
//                             (signatureRole fields are OPTIONAL - when set, the matching signature
//                             image from Company Branding is rendered above the name/underline;
//                             absent/unknown role = same text-only rendering as before)
//   {"type":"sealBlock"}     - renders the company seal/stamp image from Company Branding, if one
//                             is configured; renders nothing (no layout impact) otherwise
//   {"type":"pageBreak"}     - forces a new PDF page before the next block
//   {"type":"spacer"}
// All "text"/label/name/item values may contain {{Placeholder}} tokens - those are substituted
// separately by DocumentGenerationService AFTER blocks are rendered to HTML, so block authoring
// never needs to know about escaping.
@Service
@RequiredArgsConstructor
public class BlockRenderer {

    private final ObjectMapper objectMapper;

    // Legacy/validation-only entry point (e.g. DocumentTemplateService.validateBeforePublish just
    // needs the rendered text to scan for {{Placeholder}} tokens - it doesn't need real signature/
    // seal images resolved) - branding-dependent blocks simply render their text-only fallback.
    public String render(String contentBlocksJson) {
        return render(contentBlocksJson, null);
    }

    public String render(String contentBlocksJson, BrandingDto branding) {
        if (contentBlocksJson == null || contentBlocksJson.isBlank()) return "";
        try {
            List<Map<String, Object>> blocks = objectMapper.readValue(contentBlocksJson, List.class);
            StringBuilder html = new StringBuilder();
            for (Map<String, Object> block : blocks) {
                html.append(renderBlock(block, branding));
            }
            return html.toString();
        } catch (Exception e) {
            throw new RuntimeException("Could not parse template content blocks: " + e.getMessage(), e);
        }
    }

    private String renderBlock(Map<String, Object> block, BrandingDto branding) {
        String type = String.valueOf(block.get("type"));
        return switch (type) {
            case "heading" -> "<h1 style=\"font-size:18px;letter-spacing:1px;text-align:center;margin:0 0 20px;\">"
                    + str(block, "text") + "</h1>";
            case "paragraph" -> "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">" + str(block, "text") + "</p>";
            case "spacer" -> "<div style=\"height:16px;\"></div>";
            case "pageBreak" -> "<div style=\"page-break-before:always;\"></div>";
            case "table" -> renderTable(block);
            case "list" -> renderList(block);
            case "signatureBlock" -> renderSignatureBlock(block, branding);
            case "sealBlock" -> renderSealBlock(branding);
            default -> "";
        };
    }

    private String renderTable(Map<String, Object> block) {
        // Optional "columns" (list of header labels) turns this into a real N-column table (e.g.
        // "Fixed Components | CTC per Month | CTC Per Annum" in the Offer Letter's Annexure B) -
        // when absent, falls back to the original 2-column label/value layout every existing
        // template already uses, unchanged.
        List<String> columns = (List<String>) block.get("columns");
        if (columns != null && !columns.isEmpty()) {
            return renderMultiColumnTable(block, columns);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:6px;padding:14px 18px;margin:0 0 20px;\">");
        String title = str(block, "title");
        if (!title.isBlank()) {
            sb.append("<tr><td colspan=\"2\" style=\"padding-bottom:8px;\"><b style=\"font-size:12px;\">").append(title).append("</b></td></tr>");
        }
        List<List<String>> rows = (List<List<String>>) block.getOrDefault("rows", List.of());
        for (List<String> row : rows) {
            String label = row.size() > 0 ? row.get(0) : "";
            String value = row.size() > 1 ? row.get(1) : "";
            sb.append("<tr><td style=\"font-size:11px;color:#666;padding:3px 0;\">").append(label)
                    .append("</td><td style=\"font-size:11px;font-weight:bold;text-align:right;padding:3px 0;\">").append(value).append("</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String renderMultiColumnTable(Map<String, Object> block, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        String title = str(block, "title");
        if (!title.isBlank()) {
            sb.append("<p style=\"font-size:12px;font-weight:bold;margin:0 0 6px;\">").append(title).append("</p>");
        }
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;margin:0 0 20px;\">");
        sb.append("<tr>");
        for (int c = 0; c < columns.size(); c++) {
            sb.append("<td style=\"font-size:11px;font-weight:bold;background:#f0f0f0;padding:5px 8px;border:1px solid #ddd;")
                    .append(c == 0 ? "text-align:left;" : "text-align:right;").append("\">").append(columns.get(c)).append("</td>");
        }
        sb.append("</tr>");
        List<List<String>> rows = (List<List<String>>) block.getOrDefault("rows", List.of());
        for (List<String> row : rows) {
            sb.append("<tr>");
            for (int c = 0; c < columns.size(); c++) {
                String cell = row.size() > c ? row.get(c) : "";
                sb.append("<td style=\"font-size:11px;padding:4px 8px;border:1px solid #ddd;")
                        .append(c == 0 ? "text-align:left;" : "text-align:right;font-weight:bold;").append("\">").append(cell).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String renderList(Map<String, Object> block) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ol style=\"font-size:11px;line-height:1.8;color:#333;margin:0 0 20px;padding-left:18px;\">");
        List<String> items = (List<String>) block.getOrDefault("items", List.of());
        for (String item : items) {
            sb.append("<li>").append(item).append("</li>");
        }
        sb.append("</ol>");
        return sb.toString();
    }

    private String renderSignatureBlock(Map<String, Object> block, BrandingDto branding) {
        String leftImage = signatureImageTag(str(block, "leftSignatureRole"), branding);
        String rightImage = signatureImageTag(str(block, "rightSignatureRole"), branding);
        return "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:20px;\"><tr>"
                + "<td width=\"50%\" style=\"font-size:11px;\">" + str(block, "leftLabel") + "<br/>" + leftImage + "<br/><br/>_____________________<br/>" + str(block, "leftName") + "</td>"
                + "<td width=\"50%\" style=\"font-size:11px;\">" + str(block, "rightLabel") + "<br/>" + rightImage + "<br/><br/>_____________________<br/>" + str(block, "rightName") + "</td>"
                + "</tr></table>";
    }

    // Resolves a "CEO"/"HR"/"FINANCE" signatureRole to the matching signature image URL from
    // Company Branding, if branding is available and that field is actually set - additive only,
    // so a signatureBlock without a role (every pre-existing template) renders exactly as before.
    private String signatureImageTag(String role, BrandingDto branding) {
        if (role == null || role.isBlank() || branding == null) return "";
        String url = switch (role.toUpperCase()) {
            case "CEO" -> branding.ceoSignatureUrl();
            case "HR" -> branding.hrSignatureUrl();
            case "FINANCE" -> branding.authorizedSignatoryUrl();
            default -> null;
        };
        if (url == null || url.isBlank()) return "";
        return "<img src=\"" + escapeXml(url) + "\" style=\"max-height:60px;max-width:160px;\"/>";
    }

    private String renderSealBlock(BrandingDto branding) {
        if (branding == null || branding.companySealUrl() == null || branding.companySealUrl().isBlank()) return "";
        return "<div style=\"margin:12px 0;\"><img src=\"" + escapeXml(branding.companySealUrl()) + "\" style=\"max-height:80px;max-width:120px;\"/></div>";
    }

    // Escaped here (not left to DocumentGenerationService.substitute()) because static authored
    // text - a heading/paragraph/table cell typed straight into Document Studio, no
    // {{Placeholder}} involved at all - never passes through substitute(); a literal "&" in
    // authored text (e.g. "Terms & Conditions", "PF & ESI") broke strict-XML PDF rendering with
    // no way to fix it from the template editor. {{Placeholder}} tokens are untouched since they
    // only contain letters.
    private String str(Map<String, Object> block, String key) {
        Object value = block.get(key);
        return value != null ? escapeXml(value.toString()) : "";
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
