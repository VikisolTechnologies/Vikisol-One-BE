package com.vikisol.one.integration.provider;

// Plain calendar events (no online-meeting requirement) - e.g. blocking an interviewer's calendar
// for an in-person round, or future modules like leave-calendar sync. Kept separate from
// MeetingProvider per the requested abstraction, even though Microsoft365Provider implements both
// with the same underlying Graph call when `requireOnlineMeeting` is false.
public interface CalendarProvider {

    String getProviderName();

    boolean isConfigured();

    MeetingResult createEvent(MeetingRequest request);

    MeetingResult updateEvent(String organizerEmail, String calendarEventId, MeetingRequest request);

    void cancelEvent(String organizerEmail, String calendarEventId, String cancellationMessage);
}
