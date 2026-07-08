package com.vikisol.one.orgchart.service;

import com.vikisol.one.common.service.PdfService;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.orgchart.dto.OrgChartNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgChartService {

    private final EmployeeRepository employeeRepository;
    private final PdfService pdfService;

    public List<OrgChartNode> getFullOrgChart() {
        List<Employee> allEmployees = employeeRepository.findAll();
        List<Employee> topLevel = allEmployees.stream()
                .filter(e -> e.isActive() && e.getReportingManagerId() == null)
                .collect(Collectors.toList());

        return topLevel.stream()
                .map(e -> buildNode(e, allEmployees))
                .collect(Collectors.toList());
    }

    public OrgChartNode getOrgChartFromEmployee(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));
        List<Employee> allEmployees = employeeRepository.findAll();
        return buildNode(employee, allEmployees);
    }

    private OrgChartNode buildNode(Employee employee, List<Employee> allEmployees) {
        List<OrgChartNode> directReports = allEmployees.stream()
                .filter(e -> e.isActive() && employee.getId().equals(e.getReportingManagerId()))
                .map(e -> buildNode(e, allEmployees))
                .collect(Collectors.toList());

        return OrgChartNode.builder()
                .id(employee.getId())
                .employeeId(employee.getEmployeeId())
                .name(employee.getFirstName() + " " + employee.getLastName())
                .designation(employee.getDesignation() != null ? employee.getDesignation().getTitle() : null)
                .department(employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                .profilePictureUrl(employee.getProfilePictureUrl())
                .directReports(directReports)
                .build();
    }

    public byte[] renderOrgChartPdf() {
        List<OrgChartNode> topLevel = getFullOrgChart();
        StringBuilder body = new StringBuilder();
        if (topLevel.isEmpty()) {
            body.append("<p style=\"color:#6b7280;\">No organization data available.</p>");
        } else {
            topLevel.forEach(node -> body.append(renderNodeHtml(node)));
        }

        String xhtml = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><style>"
                + "body { font-family: Helvetica, Arial, sans-serif; color: #1f2937; }"
                + "h1 { font-size: 18px; margin-bottom: 16px; }"
                + "ul { list-style: none; margin: 0; padding-left: 18px; border-left: 1px solid #d1d5db; }"
                + "li { margin: 6px 0; }"
                + ".node { padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 6px; display: inline-block; background: #f9fafb; }"
                + ".name { font-weight: bold; font-size: 12px; }"
                + ".meta { color: #6b7280; font-size: 10px; }"
                + "</style></head><body>"
                + "<h1>Organization Chart</h1>"
                + body
                + "</body></html>";

        return pdfService.renderPdf(xhtml);
    }

    private String renderNodeHtml(OrgChartNode node) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"node\">")
                .append("<div class=\"name\">").append(escapeHtml(node.getName())).append("</div>")
                .append("<div class=\"meta\">")
                .append(escapeHtml(node.getDesignation() != null ? node.getDesignation() : ""))
                .append(node.getDepartment() != null ? " &#183; " + escapeHtml(node.getDepartment()) : "")
                .append("</div></div>");
        List<OrgChartNode> reports = node.getDirectReports();
        if (reports != null && !reports.isEmpty()) {
            html.append("<ul>");
            for (OrgChartNode child : reports) {
                html.append("<li>").append(renderNodeHtml(child)).append("</li>");
            }
            html.append("</ul>");
        }
        return html.toString();
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
