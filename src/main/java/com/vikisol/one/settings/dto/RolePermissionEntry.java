package com.vikisol.one.settings.dto;

import com.vikisol.one.security.RoleEnum;

public record RolePermissionEntry(
        RoleEnum role,
        String module,
        boolean canView
) {
}
