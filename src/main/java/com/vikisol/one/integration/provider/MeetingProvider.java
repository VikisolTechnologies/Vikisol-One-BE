package com.vikisol.one.integration.provider;

// Creates/updates/cancels an online meeting (Teams, Google Meet, ...) + its calendar event as one
// unit, since for Microsoft 365 these are the same Graph API call (a calendar event with
// `isOnlineMeeting: true` IS the Teams meeting) - splitting them into two provider calls would
// force an artificial two-step dance that doesn't match how Graph actually works.
public interface MeetingProvider {

    String getProviderName();

    boolean isConfigured();

    MeetingResult createMeeting(MeetingRequest request);

    // `organizerEmail` is required (not implied by `request.organizerEmail()`, though callers
    // should keep them consistent) since Graph's per-mailbox event path needs it explicitly and
    // a future provider might store organizer differently.
    MeetingResult updateMeeting(String organizerEmail, String calendarEventId, MeetingRequest request);

    void cancelMeeting(String organizerEmail, String calendarEventId, String cancellationMessage);
}
