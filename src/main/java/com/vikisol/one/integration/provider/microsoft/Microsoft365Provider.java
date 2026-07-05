package com.vikisol.one.integration.provider.microsoft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.one.integration.provider.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Microsoft Graph implementation of CalendarProvider/MeetingProvider/MailProvider - a single
 * class implements all three since Graph's "event with isOnlineMeeting=true" call IS both the
 * calendar event and the Teams meeting in one request, and mail send is a separate, simpler
 * Graph endpoint. Uses the OAuth2 client-credentials (app-only) flow, so once the CEO/Admin
 * registers an Azure AD app once (Company Integrations page), every subsequent call acts on
 * behalf of whichever employee mailbox is passed in (organizerEmail / sendAsEmail) - no
 * per-employee OAuth consent needed, which is what makes this usable for a shared service
 * account calling on behalf of arbitrary employees.
 *
 * NOT independently verified against a live Azure tenant in this session (no real tenant/app
 * registration was available to test against) - the request/response shapes follow Microsoft's
 * published Graph v1.0 API contract, but this should be smoke-tested against a real Microsoft
 * 365 tenant before being relied on in production.
 */
@Slf4j
public class Microsoft365Provider implements CalendarProvider, MeetingProvider, MailProvider {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";

    // Cached app-only token - avoids requesting a new token on every single Graph call (tokens
    // are valid ~60-90 minutes); refreshed a minute early to avoid edge-of-expiry failures.
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private final Object tokenLock = new Object();

    public Microsoft365Provider(String tenantId, String clientId, String clientSecret) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public String getProviderName() {
        return "Microsoft 365";
    }

    @Override
    public boolean isConfigured() {
        return tenantId != null && !tenantId.isBlank() && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    private String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) return cachedToken;
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) return cachedToken;
            try {
                String form = "client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8")
                        + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, "UTF-8")
                        + "&scope=" + java.net.URLEncoder.encode("https://graph.microsoft.com/.default", "UTF-8")
                        + "&grant_type=client_credentials";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    throw new RuntimeException("Azure AD token request failed (" + response.statusCode() + "): " + response.body());
                }
                JsonNode json = objectMapper.readTree(response.body());
                cachedToken = json.get("access_token").asText();
                int expiresInSeconds = json.has("expires_in") ? json.get("expires_in").asInt() : 3600;
                tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresInSeconds - 60));
                return cachedToken;
            } catch (Exception e) {
                throw new RuntimeException("Could not acquire Microsoft Graph access token: " + e.getMessage(), e);
            }
        }
    }

    /** Used by the "Test Connection" button on the Company Integrations page. */
    public void testConnection() {
        getAccessToken();
    }

    private HttpRequest.Builder graphRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(GRAPH_BASE + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + getAccessToken())
                .header("Content-Type", "application/json");
    }

    private Map<String, Object> buildEventBody(MeetingRequest request) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("subject", request.subject());
        body.put("body", Map.of("contentType", "HTML", "content", request.bodyHtml() != null ? request.bodyHtml() : ""));
        String tz = request.timezone() != null ? request.timezone() : "UTC";
        body.put("start", Map.of("dateTime", request.start().toString(), "timeZone", tz));
        body.put("end", Map.of("dateTime", request.end().toString(), "timeZone", tz));
        if (request.location() != null) body.put("location", Map.of("displayName", request.location()));
        body.put("attendees", request.attendeeEmails().stream()
                .map(email -> Map.of("emailAddress", Map.of("address", email), "type", "required"))
                .toList());
        if (request.requireOnlineMeeting()) {
            body.put("isOnlineMeeting", true);
            body.put("onlineMeetingProvider", "teamsForBusiness");
        }
        return body;
    }

    private MeetingResult parseEventResponse(String responseBody) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);
        String eventId = json.has("id") ? json.get("id").asText() : null;
        JsonNode onlineMeeting = json.get("onlineMeeting");
        String joinUrl = onlineMeeting != null && onlineMeeting.has("joinUrl") ? onlineMeeting.get("joinUrl").asText() : null;
        String teamsMeetingId = onlineMeeting != null && onlineMeeting.has("conferenceId") ? onlineMeeting.get("conferenceId").asText() : null;
        return new MeetingResult(eventId, eventId, teamsMeetingId, joinUrl);
    }

    private String organizerPath(MeetingRequest request) {
        if (request.organizerEmail() == null || request.organizerEmail().isBlank()) {
            throw new IllegalArgumentException("organizerEmail is required to create a Microsoft 365 calendar event/meeting");
        }
        return "/users/" + request.organizerEmail() + "/events";
    }

    private MeetingResult createEventOrMeeting(MeetingRequest request) {
        try {
            String body = objectMapper.writeValueAsString(buildEventBody(request));
            HttpRequest httpRequest = graphRequest(organizerPath(request))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Graph event creation failed (" + response.statusCode() + "): " + response.body());
            }
            return parseEventResponse(response.body());
        } catch (Exception e) {
            log.error("Microsoft 365 event/meeting creation failed: {}", e.getMessage());
            throw new RuntimeException("Could not create Microsoft 365 calendar event/Teams meeting: " + e.getMessage(), e);
        }
    }

    private MeetingResult updateEventOrMeeting(String organizerEmail, String eventId, MeetingRequest request) {
        try {
            String body = objectMapper.writeValueAsString(buildEventBody(request));
            HttpRequest httpRequest = graphRequest("/users/" + organizerEmail + "/events/" + eventId)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Graph event update failed (" + response.statusCode() + "): " + response.body());
            }
            return parseEventResponse(response.body());
        } catch (Exception e) {
            log.error("Microsoft 365 event/meeting update failed: {}", e.getMessage());
            throw new RuntimeException("Could not update Microsoft 365 calendar event/Teams meeting: " + e.getMessage(), e);
        }
    }

    private void cancelEventOrMeeting(String organizerEmail, String eventId, String cancellationMessage) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("comment", cancellationMessage != null ? cancellationMessage : "This meeting has been cancelled."));
            HttpRequest httpRequest = graphRequest("/users/" + organizerEmail + "/events/" + eventId + "/cancel")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Graph event cancellation failed (" + response.statusCode() + "): " + response.body());
            }
        } catch (Exception e) {
            log.error("Microsoft 365 event/meeting cancellation failed: {}", e.getMessage());
            throw new RuntimeException("Could not cancel Microsoft 365 calendar event/Teams meeting: " + e.getMessage(), e);
        }
    }

    // ─── MeetingProvider (Teams) ───

    @Override
    public MeetingResult createMeeting(MeetingRequest request) {
        return createEventOrMeeting(new MeetingRequest(request.subject(), request.bodyHtml(), request.start(), request.end(),
                request.timezone(), request.attendeeEmails(), request.organizerEmail(), request.location(), true));
    }

    @Override
    public MeetingResult updateMeeting(String organizerEmail, String calendarEventId, MeetingRequest request) {
        return updateEventOrMeeting(organizerEmail, calendarEventId, new MeetingRequest(request.subject(), request.bodyHtml(), request.start(), request.end(),
                request.timezone(), request.attendeeEmails(), request.organizerEmail(), request.location(), true));
    }

    @Override
    public void cancelMeeting(String organizerEmail, String calendarEventId, String cancellationMessage) {
        cancelEventOrMeeting(organizerEmail, calendarEventId, cancellationMessage);
    }

    // ─── CalendarProvider (plain events, no Teams) ───

    @Override
    public MeetingResult createEvent(MeetingRequest request) {
        return createEventOrMeeting(request);
    }

    @Override
    public MeetingResult updateEvent(String organizerEmail, String calendarEventId, MeetingRequest request) {
        return updateEventOrMeeting(organizerEmail, calendarEventId, request);
    }

    @Override
    public void cancelEvent(String organizerEmail, String calendarEventId, String cancellationMessage) {
        cancelMeeting(organizerEmail, calendarEventId, cancellationMessage);
    }

    // ─── MailProvider ───

    @Override
    public void sendMail(MailMessage message) {
        try {
            Map<String, Object> emailBody = new java.util.HashMap<>();
            emailBody.put("subject", message.subject());
            emailBody.put("body", Map.of("contentType", "HTML", "content", message.htmlBody()));
            emailBody.put("toRecipients", message.to().stream().map(e -> Map.of("emailAddress", Map.of("address", e))).toList());
            if (message.cc() != null && !message.cc().isEmpty()) {
                emailBody.put("ccRecipients", message.cc().stream().map(e -> Map.of("emailAddress", Map.of("address", e))).toList());
            }
            if (message.attachments() != null && !message.attachments().isEmpty()) {
                emailBody.put("attachments", message.attachments().stream().map(a -> Map.of(
                        "@odata.type", "#microsoft.graph.fileAttachment",
                        "name", a.filename(),
                        "contentBytes", Base64.getEncoder().encodeToString(a.content())
                )).toList());
            }
            Map<String, Object> payload = Map.of("message", emailBody, "saveToSentItems", true);
            String sendAs = message.sendAsEmail() != null && !message.sendAsEmail().isBlank() ? message.sendAsEmail() : message.to().get(0);
            HttpRequest httpRequest = graphRequest("/users/" + sendAs + "/sendMail")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Graph sendMail failed (" + response.statusCode() + "): " + response.body());
            }
        } catch (Exception e) {
            log.error("Microsoft 365 sendMail failed: {}", e.getMessage());
            throw new RuntimeException("Could not send email via Microsoft 365: " + e.getMessage(), e);
        }
    }
}
