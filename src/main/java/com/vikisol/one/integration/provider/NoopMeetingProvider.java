package com.vikisol.one.integration.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Default provider until Microsoft 365 (or Google Workspace, later) is configured on the Company
// Integrations page - creates no real meeting/calendar event; the caller (RecruitmentService)
// keeps relying on the recruiter's manually-entered meeting link, exactly like before this
// integration layer existed. This is what makes the rollout non-breaking: nothing regresses for
// a company that hasn't configured Microsoft 365 yet.
@Slf4j
@Component
public class NoopMeetingProvider implements MeetingProvider, CalendarProvider {

    @Override
    public String getProviderName() {
        return "None (manual meeting link)";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public MeetingResult createMeeting(MeetingRequest request) {
        return MeetingResult.none();
    }

    @Override
    public MeetingResult updateMeeting(String organizerEmail, String calendarEventId, MeetingRequest request) {
        return MeetingResult.none();
    }

    @Override
    public void cancelMeeting(String organizerEmail, String calendarEventId, String cancellationMessage) {
        log.debug("No meeting provider configured - nothing to cancel externally for event {}", calendarEventId);
    }

    @Override
    public MeetingResult createEvent(MeetingRequest request) {
        return MeetingResult.none();
    }

    @Override
    public MeetingResult updateEvent(String organizerEmail, String calendarEventId, MeetingRequest request) {
        return MeetingResult.none();
    }

    @Override
    public void cancelEvent(String organizerEmail, String calendarEventId, String cancellationMessage) {
        log.debug("No calendar provider configured - nothing to cancel externally for event {}", calendarEventId);
    }
}
