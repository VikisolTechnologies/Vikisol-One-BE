package com.vikisol.one.orgchart.service;

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
}
