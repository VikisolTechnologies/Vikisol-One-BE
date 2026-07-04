package com.vikisol.one.doctemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
//                             "rightLabel":"Accepted by","rightName":"{{EmployeeName}}"}
//   {"type":"spacer"}
// All "text"/label/name/item values may contain {{Placeholder}} tokens - those are substituted
// separately by DocumentGenerationService AFTER blocks are rendered to HTML, so block authoring
// never needs to know about escaping.
@Service
@RequiredArgsConstructor
public class BlockRenderer {

    private final ObjectMapper objectMapper;

    public String render(String contentBlocksJson) {
        if (contentBlocksJson == null || contentBlocksJson.isBlank()) return "";
        try {
            List<Map<String, Object>> blocks = objectMapper.readValue(contentBlocksJson, List.class);
            StringBuilder html = new StringBuilder();
            for (Map<String, Object> block : blocks) {
                html.append(renderBlock(block));
            }
            return html.toString();
        } catch (Exception e) {
            throw new RuntimeException("Could not parse template content blocks: " + e.getMessage(), e);
        }
    }

    private String renderBlock(Map<String, Object> block) {
        String type = String.valueOf(block.get("type"));
        return switch (type) {
            case "heading" -> "<h1 style=\"font-size:18px;letter-spacing:1px;text-align:center;margin:0 0 20px;\">"
                    + str(block, "text") + "</h1>";
            case "paragraph" -> "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">" + str(block, "text") + "</p>";
            case "spacer" -> "<div style=\"height:16px;\"></div>";
            case "table" -> renderTable(block);
            case "list" -> renderList(block);
            case "signatureBlock" -> renderSignatureBlock(block);
            default -> "";
        };
    }

    private String renderTable(Map<String, Object> block) {
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

    private String renderSignatureBlock(Map<String, Object> block) {
        return "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:20px;\"><tr>"
                + "<td width=\"50%\" style=\"font-size:11px;\">" + str(block, "leftLabel") + "<br/><br/><br/>_____________________<br/>" + str(block, "leftName") + "</td>"
                + "<td width=\"50%\" style=\"font-size:11px;\">" + str(block, "rightLabel") + "<br/><br/><br/>_____________________<br/>" + str(block, "rightName") + "</td>"
                + "</tr></table>";
    }

    private String str(Map<String, Object> block, String key) {
        Object value = block.get(key);
        return value != null ? value.toString() : "";
    }
}
