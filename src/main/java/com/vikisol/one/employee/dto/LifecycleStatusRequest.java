package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.Employee;

public record LifecycleStatusRequest(
        Employee.LifecycleStatus status
) {
}
