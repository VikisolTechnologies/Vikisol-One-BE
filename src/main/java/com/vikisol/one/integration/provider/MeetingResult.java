package com.vikisol.one.integration.provider;

// What the Recruitment module actually persists on Interview (externalMeetingId/
// externalCalendarEventId/externalTeamsMeetingId) - deliberately provider-neutral field names so
// swapping Microsoft 365 for Google Workspace later doesn't require a schema change, just a
// different provider implementation populating the same 3 ids.
public record MeetingResult(
        String calendarEventId,
        String meetingId,
        String teamsMeetingId,
        String joinUrl
) {
    public static MeetingResult none() {
        return new MeetingResult(null, null, null, null);
    }
}
