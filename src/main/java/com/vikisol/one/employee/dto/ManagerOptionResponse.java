package com.vikisol.one.employee.dto;

import java.util.UUID;

// Lightweight manager list for dropdowns (e.g. recruiter selecting a reporting manager) -
// exposed to roles that can't call the full employee list endpoint.
public record ManagerOptionResponse(UUID id, String name, String designationTitle) {
}
