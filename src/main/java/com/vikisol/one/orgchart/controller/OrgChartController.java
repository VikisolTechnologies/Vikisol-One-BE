package com.vikisol.one.orgchart.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.orgchart.dto.OrgChartNode;
import com.vikisol.one.orgchart.service.OrgChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/org-chart")
@RequiredArgsConstructor
public class OrgChartController {

    private final OrgChartService orgChartService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrgChartNode>>> getFullOrgChart() {
        List<OrgChartNode> orgChart = orgChartService.getFullOrgChart();
        return ResponseEntity.ok(new ApiResponse<>(true, "Org chart retrieved successfully", orgChart));
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> downloadOrgChartPdf() {
        byte[] pdf = orgChartService.renderOrgChartPdf();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Organization_Chart.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<OrgChartNode>> getOrgChartFromEmployee(@PathVariable UUID employeeId) {
        OrgChartNode node = orgChartService.getOrgChartFromEmployee(employeeId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Org chart retrieved successfully", node));
    }
}
