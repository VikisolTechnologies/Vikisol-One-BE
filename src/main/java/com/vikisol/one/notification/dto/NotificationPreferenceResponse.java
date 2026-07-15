package com.vikisol.one.notification.dto;

public record NotificationPreferenceResponse(
        boolean emailNotifications,
        boolean pushNotifications,
        boolean leaveReminders,
        boolean timesheetReminders,
        boolean birthdayReminders,
        boolean interviewReminders,
        boolean payrollAlerts
) {}
