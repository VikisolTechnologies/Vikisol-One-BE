package com.vikisol.one.integration.provider;

import java.time.LocalDateTime;
import java.util.List;

// Provider-agnostic request to create/update a meeting+calendar event - the Recruitment module
// builds one of these and hands it to whichever CalendarProvider/MeetingProvider is currently
// configured; it never talks to Microsoft Graph or Google Calendar directly.
public record MeetingRequest(
        String subject,
        String bodyHtml,
        LocalDateTime start,
        LocalDateTime end,
        String timezone,
        List<String> attendeeEmails,
        String organizerEmail,
        String location,
        boolean requireOnlineMeeting
) {
}
