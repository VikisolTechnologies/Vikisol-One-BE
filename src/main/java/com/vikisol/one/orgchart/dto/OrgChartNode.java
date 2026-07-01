package com.vikisol.one.orgchart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgChartNode {
    private UUID id;
    private String employeeId;
    private String name;
    private String designation;
    private String department;
    private String profilePictureUrl;
    private List<OrgChartNode> directReports;
}
