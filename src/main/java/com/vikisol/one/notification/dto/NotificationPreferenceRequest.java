package com.vikisol.one.notification.dto;

// Nullable Boolean fields - only the ones actually present/changed in a PUT get applied,
// so toggling one switch on the frontend never has to resend the other six.
public record NotificationPreferenceRequest(
        Boolean emailNotifications,
        Boolean pushNotifications,
        Boolean leaveReminders,
        Boolean timesheetReminders,
        Boolean birthdayReminders,
        Boolean interviewReminders,
        Boolean payrollAlerts
) {}
