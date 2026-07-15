package com.vikisol.one.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.one.communication.entity.EmailLog;
import com.vikisol.one.communication.service.EmailLogService;
import com.vikisol.one.emailtemplate.entity.EmailTemplate.TemplateKey;
import com.vikisol.one.emailtemplate.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Railway (and most cloud hosts) block outbound SMTP ports, so raw SMTP to GoDaddy never connects.
// Resend's HTTPS API sends without needing SMTP at all.
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ObjectMapper objectMapper;
    private final EmailLogService emailLogService;
    private final EmailTemplateService emailTemplateService;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from:Vikisol One <connect@vikisol.in>}")
    private String fromEmail;

    // Resend never touches the actual mailbox, so nothing shows in its Sent folder.
    // BCC'ing it on every send gives a record there (in Inbox, not Sent, but visible).
    @Value("${resend.bcc:connect@vikisol.in}")
    private String bccEmail;

    @Value("${resend.support-email:connect@vikisol.in}")
    private String supportEmail;

    // Overridable via LOGO_URL env var so the logo can be swapped without a code change/redeploy.
    @Value("${app.logo-url:https://res.cloudinary.com/drqgvncx1/image/upload/v1781621022/snipped_fccgpm.png}")
    private String logoUrl;

    // Base URL of the deployed frontend, used to build links (activation, etc.) inside emails.
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** A file to attach to an outgoing email - filename plus raw bytes. */
    public record Attachment(String filename, byte[] content) {}

    private void send(String to, String subject, String htmlBody) throws Exception {
        send(to, subject, htmlBody, List.of());
    }

    private void send(String to, String subject, String htmlBody, List<Attachment> attachments) throws Exception {
        send(List.of(to), List.of(), subject, htmlBody, attachments);
    }

    // To/CC-aware variant - used by the interview-invite flow where the candidate + primary
    // interviewer are the real audience (To) and the recruiter/HR/additional interviewers just
    // need visibility (CC), rather than every recipient getting an identical "To" email.
    private void send(List<String> to, List<String> cc, String subject, String htmlBody, List<Attachment> attachments) throws Exception {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured");
        }
        Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                "from", fromEmail,
                "to", to,
                "subject", subject,
                "html", htmlBody));
        if (cc != null && !cc.isEmpty()) {
            payload.put("cc", cc);
        }
        if (bccEmail != null && !bccEmail.isBlank()) {
            payload.put("bcc", List.of(bccEmail));
        }
        if (attachments != null && !attachments.isEmpty()) {
            payload.put("attachments", attachments.stream()
                    .map(a -> Map.of(
                            "filename", a.filename(),
                            "content", java.util.Base64.getEncoder().encodeToString(a.content())))
                    .toList());
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Resend API returned " + response.statusCode() + ": " + response.body());
        }
    }

    // Public, provider-agnostic entry point used by NoopMailProvider (the fallback used when no
    // real MailProvider, e.g. Microsoft 365, is configured) - lets the integration layer send
    // through this app's existing Resend account without duplicating the request-building logic.
    @Async
    public void sendRaw(List<String> to, List<String> cc, String subject, String htmlBody, List<Attachment> attachments) {
        String recipient = (to != null && !to.isEmpty()) ? String.join(",", to) : "unknown";
        try {
            send(to, cc, subject, htmlBody, attachments);
            log.info("Email sent (raw) to {} cc {}: {}", to, cc, subject);
            emailLogService.log(recipient, subject, EmailLog.Category.OTHER, EmailLog.Status.SENT, null, null);
        } catch (Exception e) {
            log.warn("Failed to send raw email to {}: {}", to, e.getMessage());
            emailLogService.log(recipient, subject, EmailLog.Category.OTHER, EmailLog.Status.FAILED, null, e.getMessage());
        }
    }

    public void sendEmail(String to, String subject, String body) {
        sendEmail(to, subject, body, EmailLog.Category.OTHER, null);
    }

    // Category/employee-id aware variant - used by the internal notification helpers below so the
    // Communication Center audit log carries a meaningful category instead of everything landing
    // under OTHER. Kept as an overload rather than touching every external call site.
    @Async
    public void sendEmail(String to, String subject, String body, EmailLog.Category category, java.util.UUID relatedEmployeeId) {
        try {
            send(to, subject, "<p style=\"white-space:pre-wrap;font-family:Segoe UI,Arial,sans-serif;\">" + body + "</p>");
            log.info("Email sent to {}: {}", to, subject);
            emailLogService.log(to, subject, category, EmailLog.Status.SENT, relatedEmployeeId, null);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
            emailLogService.log(to, subject, category, EmailLog.Status.FAILED, relatedEmployeeId, e.getMessage());
        }
    }

    // Synchronous (not @Async) so callers get a real success/failure result back, for email diagnostics.
    public void sendTestEmail(String to) throws Exception {
        send(to, "Vikisol One - Email delivery test",
                brandedTemplate("Email connectivity test",
                        "<p style=\"color:#333;font-size:14px;\">This is a test email confirming " + fromEmail + " is correctly wired up to Vikisol One via Resend.</p>"));
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        sendHtmlEmail(to, subject, htmlBody, EmailLog.Category.OTHER, null);
    }

    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody, EmailLog.Category category, java.util.UUID relatedEmployeeId) {
        try {
            send(to, subject, htmlBody);
            log.info("HTML email sent to {}: {}", to, subject);
            emailLogService.log(to, subject, category, EmailLog.Status.SENT, relatedEmployeeId, null);
        } catch (Exception e) {
            log.warn("Failed to send HTML email to {}: {}", to, e.getMessage());
            emailLogService.log(to, subject, category, EmailLog.Status.FAILED, relatedEmployeeId, e.getMessage());
        }
    }

    public void sendHtmlEmailWithAttachment(String to, String subject, String htmlBody, Attachment attachment) {
        sendHtmlEmailWithAttachment(to, subject, htmlBody, attachment, EmailLog.Category.OTHER, null);
    }

    @Async
    public void sendHtmlEmailWithAttachment(String to, String subject, String htmlBody, Attachment attachment,
                                             EmailLog.Category category, java.util.UUID relatedEmployeeId) {
        try {
            send(to, subject, htmlBody, List.of(attachment));
            log.info("HTML email with attachment sent to {}: {}", to, subject);
            emailLogService.log(to, subject, category, EmailLog.Status.SENT, relatedEmployeeId, null);
        } catch (Exception e) {
            log.warn("Failed to send HTML email with attachment to {}: {}", to, e.getMessage());
            emailLogService.log(to, subject, category, EmailLog.Status.FAILED, relatedEmployeeId, e.getMessage());
        }
    }

    // Bulk Payslip Email - one payslip PDF attached per send, used in a loop by
    // PayrollService.bulkEmailPayslips. @Async here (not just relying on the 4-arg overload it
    // calls, which is a self-invocation the proxy can't intercept) so PayrollService's loop
    // dispatches each send to the background instead of blocking on N sequential SMTP round-trips.
    @Async
    public void sendPayslipEmail(String officialEmail, String firstName, String payPeriodTitle, byte[] pdfBytes, String fileName) {
        String subject = payPeriodTitle;
        String body =
                "<p style=\"margin:0 0 16px;color:#444;\">Dear " + firstName + ",</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Please find attached your payslip for this period.</p>"
                + signatureBlock("Regards", "Vikisol One Payroll Team");
        sendHtmlEmailWithAttachment(officialEmail, subject, brandedTemplate(payPeriodTitle, body),
                new Attachment(fileName, pdfBytes), EmailLog.Category.OTHER, null);
    }

    // Multi-attachment variant - needed for the offboarding "exit package" email, which bundles
    // whichever generated documents (experience letter, relieving letter, payslips, etc.) actually
    // exist for the employee rather than a single PDF.
    public void sendHtmlEmailWithAttachments(String to, String subject, String htmlBody, List<Attachment> attachments) {
        sendHtmlEmailWithAttachments(to, subject, htmlBody, attachments, EmailLog.Category.OTHER, null);
    }

    @Async
    public void sendHtmlEmailWithAttachments(String to, String subject, String htmlBody, List<Attachment> attachments,
                                              EmailLog.Category category, java.util.UUID relatedEmployeeId) {
        try {
            send(to, subject, htmlBody, attachments);
            log.info("HTML email with {} attachment(s) sent to {}: {}", attachments.size(), to, subject);
            emailLogService.log(to, subject, category, EmailLog.Status.SENT, relatedEmployeeId, null);
        } catch (Exception e) {
            log.warn("Failed to send HTML email with attachments to {}: {}", to, e.getMessage());
            emailLogService.log(to, subject, category, EmailLog.Status.FAILED, relatedEmployeeId, e.getMessage());
        }
    }

    /** Wraps body content in Vikisol's black/white/orange brand shell. */
    private String brandedTemplate(String preheader, String bodyHtml) {
        return "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f4f5;font-family:Segoe UI,Helvetica,Arial,sans-serif;\">"
                + "<span style=\"display:none;max-height:0;overflow:hidden;\">" + preheader + "</span>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);\">"
                + "<tr><td style=\"background:#0a0a0a;padding:28px 32px;\">"
                + "<img src=\"" + logoUrl + "\" alt=\"Vikisol\" style=\"height:40px;display:block;\" />"
                + "<div style=\"color:#9a9a9a;font-size:10px;letter-spacing:2px;margin-top:10px;\">TECHNOLOGY &nbsp;&bull;&nbsp; TALENT &nbsp;&bull;&nbsp; TRANSFORMATION</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:32px;color:#1a1a1a;font-size:14px;line-height:1.6;\">" + bodyHtml + "</td></tr>"
                + "<tr><td style=\"background:#f4f4f5;padding:20px 32px;color:#777;font-size:11px;text-align:center;line-height:1.8;\">"
                + "<b>Vikisol Technologies Pvt Ltd</b><br/>"
                + "Technology &bull; Talent &bull; Transformation<br/>"
                + "Need Help? " + supportEmail + " &middot; www.vikisol.in"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String rowHtml(String label, String value) {
        return "<tr><td style=\"padding:6px 0;color:#666;font-size:13px;\">" + label + "</td>"
                + "<td style=\"padding:6px 0;color:#1a1a1a;font-size:13px;font-weight:600;text-align:right;\">" + value + "</td></tr>";
    }

    /** Formal closing block used across offer/hike/resignation letters, e.g. signatureBlock("Warm regards", "Talent Acquisition Team"). */
    private String signatureBlock(String closing, String signatoryTitle) {
        return "<p style=\"margin:28px 0 0;color:#1a1a1a;\">" + closing + ",<br/>"
                + "<b>" + signatoryTitle + "</b><br/>"
                + "Vikisol Technologies Pvt Ltd</p>";
    }

    // Direct, ad-hoc email from a recruiter to a candidate (e.g. "Send Email" on the candidate
    // profile) - unlike the templated offer/interview/assessment emails, the subject and body are
    // freely typed by the sender, so this just wraps whatever they wrote in the standard branded
    // shell rather than a fixed template.
    @Async
    public void sendCandidateEmail(String candidateEmail, String candidateName, String subject, String message, String senderName) {
        String body =
                "<p style=\"margin:0 0 16px;color:#444;\">Dear " + candidateName + ",</p>"
                + "<div style=\"margin:0 0 20px;color:#444;white-space:pre-wrap;\">" + message.replace("<", "&lt;").replace(">", "&gt;") + "</div>"
                + signatureBlock("Regards", senderName != null && !senderName.isBlank() ? senderName : "Vikisol One Recruitment Team");
        sendHtmlEmail(candidateEmail, subject, brandedTemplate(subject, body), EmailLog.Category.OTHER, null);
    }

    // Sent when a login succeeds from a device/IP never seen before for this user (see
    // AuthService.maybeSendLoginAlert) - goes to the personal/recovery email, same reasoning as
    // password-reset: if the official mailbox itself was compromised, the alert still reaches them.
    //
    // Must be @Async here, not just on the sendHtmlEmail() overload it calls internally - Spring's
    // proxy-based @Async only intercepts calls that come in through the bean's proxy from OUTSIDE
    // the class. sendHtmlEmail() being @Async did nothing for this method, since it was called as
    // a plain self-invocation (this.sendHtmlEmail(...)), bypassing the proxy entirely and running
    // the real SMTP send synchronously - which meant every login from a device/IP the user hadn't
    // used before blocked on a live outbound SMTP round-trip before the login response returned.
    // AuthService calling THIS method, though, is a genuine external call through the proxy, so
    // marking it here actually works.
    @Async
    public void sendNewLoginAlertEmail(String personalEmail, String name, String device, String ipAddress, String whenText) {
        String subject = "New sign-in to your Vikisol One account";
        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">New sign-in detected</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Dear " + name + ",</p>"
                + "<p style=\"margin:0 0 16px;color:#444;\">We noticed a sign-in to your Vikisol One account from a device we haven't seen before:</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + rowHtml("Device", device)
                + rowHtml("IP Address", ipAddress != null ? ipAddress : "Unknown")
                + rowHtml("Time", whenText)
                + "</table>"
                + "<p style=\"margin:0 0 16px;color:#444;\">If this was you, no action is needed.</p>"
                + "<p style=\"margin:0;color:#444;\"><b>If this wasn't you</b>, please change your password immediately and review your active sessions in Vikisol One.</p>"
                + signatureBlock("Regards", "Vikisol One Security");
        sendHtmlEmail(personalEmail, subject, brandedTemplate("New sign-in to your account", body), EmailLog.Category.OTHER, null);
    }

    public void sendLeaveApprovalNotification(String employeeEmail, String employeeName, String status, String leaveType, String dates) {
        String subject = "Leave " + status + " - " + leaveType;
        String body = String.format("Dear %s,\n\nYour %s request for %s has been %s.\n\nRegards,\nVikisol One HR", employeeName, leaveType, dates, status.toLowerCase());
        sendEmail(employeeEmail, subject, body, EmailLog.Category.REMINDER, null);
    }

    public void sendLeaveApplicationNotification(String managerEmail, String employeeName, String leaveType, String dates) {
        String subject = "Leave Application - " + employeeName;
        String body = String.format("Dear Manager,\n\n%s has applied for %s from %s.\nPlease review and take action.\n\nRegards,\nVikisol One HR", employeeName, leaveType, dates);
        sendEmail(managerEmail, subject, body, EmailLog.Category.REMINDER, null);
    }

    public void sendPayrollProcessedNotification(String email, String name, String month, String year) {
        String subject = "Payslip Generated - " + month + "/" + year;
        String body = String.format("Dear %s,\n\nYour payslip for %s/%s has been generated. Please log in to view details.\n\nRegards,\nVikisol One HR", name, month, year);
        sendEmail(email, subject, body, EmailLog.Category.REMINDER, null);
    }

    public void sendTicketNotification(String email, String ticketNumber, String title, String status) {
        String subject = "Ticket " + ticketNumber + " - " + status;
        String body = String.format("Ticket %s: %s\nStatus: %s\n\nPlease log in for details.\n\nRegards,\nVikisol One", ticketNumber, title, status);
        sendEmail(email, subject, body, EmailLog.Category.REMINDER, null);
    }

    // Builds a minimal RFC 5545 .ics calendar invite so the interview shows up on the recipient's
    // calendar app regardless of email client - Resend has no native calendar-invite feature, so
    // this is attached as a plain file the same way the offer-letter PDF is.
    private byte[] buildIcs(String uid, String summary, String description, String location,
                             java.time.LocalDateTime start, int durationMinutes, String organizerEmail) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        java.time.LocalDateTime end = start.plusMinutes(durationMinutes);
        String escapedDescription = description == null ? "" : description.replace("\n", "\\n").replace(",", "\\,");
        String ics = "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//Vikisol One//Interview Scheduling//EN\r\n"
                + "METHOD:REQUEST\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:" + uid + "@vikisol.in\r\n"
                + "DTSTAMP:" + java.time.LocalDateTime.now().format(fmt) + "\r\n"
                + "DTSTART:" + start.format(fmt) + "\r\n"
                + "DTEND:" + end.format(fmt) + "\r\n"
                + "SUMMARY:" + summary + "\r\n"
                + "DESCRIPTION:" + escapedDescription + "\r\n"
                + "LOCATION:" + (location == null ? "" : location.replace(",", "\\,")) + "\r\n"
                + "ORGANIZER:mailto:" + organizerEmail + "\r\n"
                + "STATUS:CONFIRMED\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";
        return ics.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Pure content (subject/html/ics) for an interview invite - no sending, so any MailProvider (Resend fallback or Microsoft 365) can dispatch it identically. */
    public record InterviewEmailContent(String subject, String html, byte[] ics) {}

    /**
     * Builds the interview invitation content - job description/tech stack pulled in from the job
     * posting, plus a .ics calendar payload the caller can attach. Signed by the recruiter who
     * scheduled it, not a generic team signature, since the candidate should know who to reach
     * out to. Building this separately from sending lets the same content go out either via the
     * default Resend account or, once configured, via the tenant's actual Microsoft 365 mailbox -
     * see IntegrationService/MailProvider.
     */
    public InterviewEmailContent buildInterviewInviteEmail(
            String candidateName, String jobTitle, String jobDescription, String techStack,
            String interviewTitle, String interviewType, java.time.LocalDate date, java.time.LocalTime time,
            int durationMinutes, String timezone, String platform, String meetingLink, String location,
            String agenda, String recruiterName, String recruiterEmail, String interviewId) {
        String subject = interviewTitle != null && !interviewTitle.isBlank()
                ? interviewTitle
                : jobTitle + " - " + interviewType + " Interview";

        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Interview Scheduled</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Dear " + candidateName + ",</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your interview for the <b>" + jobTitle + "</b> position has been scheduled. Details below:</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + rowHtml("Interview", subject)
                + rowHtml("Type", interviewType)
                + rowHtml("Date", date.toString())
                + rowHtml("Time", time.toString() + (timezone != null ? " (" + timezone + ")" : ""))
                + rowHtml("Duration", durationMinutes + " minutes")
                + rowHtml("Platform", platform)
                + (meetingLink != null && !meetingLink.isBlank() ? rowHtml("Meeting Link", "<a href=\"" + meetingLink + "\">" + meetingLink + "</a>") : "")
                + (location != null && !location.isBlank() ? rowHtml("Location", location) : "")
                + "</table>"
                + (agenda != null && !agenda.isBlank() ? "<p style=\"margin:0 0 8px;font-weight:600;\">Agenda</p><p style=\"margin:0 0 20px;color:#444;white-space:pre-wrap;\">" + agenda + "</p>" : "")
                + (jobDescription != null && !jobDescription.isBlank() ? "<p style=\"margin:0 0 8px;font-weight:600;\">Role Overview</p><p style=\"margin:0 0 20px;color:#444;white-space:pre-wrap;\">" + jobDescription + "</p>" : "")
                + (techStack != null && !techStack.isBlank() ? "<p style=\"margin:0 0 8px;font-weight:600;\">Tech Stack</p><p style=\"margin:0 0 20px;color:#444;\">" + techStack + "</p>" : "")
                + "<p style=\"margin:20px 0 0;color:#444;\">Please be online/present 5 minutes before the scheduled time. A calendar invite is attached to this email.</p>"
                + signatureBlock("Best regards", recruiterName != null && !recruiterName.isBlank() ? recruiterName : "Talent Acquisition Team");

        String html = brandedTemplate("Interview scheduled - " + jobTitle, body);
        byte[] ics = buildIcs(interviewId, subject, agenda, meetingLink != null ? meetingLink : location,
                java.time.LocalDateTime.of(date, time), durationMinutes > 0 ? durationMinutes : 30,
                recruiterEmail != null ? recruiterEmail : supportEmail);

        return new InterviewEmailContent(subject, html, ics);
    }

    public void sendOfferLetterEmail(String email, String candidateName, String employeeId, String designation,
                                      BigDecimal annualCtc, Map<String, BigDecimal> ctcBreakup, LocalDate dateOfJoining,
                                      String reportingManagerName, byte[] offerLetterPdf) {
        String subject = "Congratulations " + candidateName + " - Your Offer from Vikisol Technologies";

        StringBuilder breakupRows = new StringBuilder();
        if (ctcBreakup != null) {
            ctcBreakup.forEach((k, v) -> {
                if (!"ctc".equals(k)) {
                    String label = k.replaceAll("([A-Z])", " $1");
                    label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
                    breakupRows.append(rowHtml(label, "&#8377; " + v));
                }
            });
        }

        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Congratulations, " + candidateName + "! &#127881;</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">We are delighted to confirm that you have been selected to join <b>Vikisol Technologies Pvt Ltd</b>.</p>"
                + "<p style=\"margin:0 0 20px;\">We are pleased to offer you the position of <b>" + designation + "</b>"
                + (reportingManagerName != null && !reportingManagerName.isBlank() ? ", reporting to <b>" + reportingManagerName + "</b>" : "")
                + ", based on your experience and the discussions we've had with you.</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + rowHtml("Employee ID", employeeId)
                + rowHtml("Designation", designation)
                + rowHtml("Annual CTC", "&#8377; " + annualCtc)
                + rowHtml("Date of Joining", String.valueOf(dateOfJoining))
                + "</table>"
                + "<p style=\"margin:20px 0 8px;font-weight:600;\">Monthly CTC Breakup</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + breakupRows
                + "</table>"
                + "<p style=\"margin:0 0 8px;font-weight:600;\">What's Next?</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your official Offer Letter has been attached to this email for your review. Kindly sign and upload the accepted copy (if applicable) before the expiry date mentioned in the letter. If you have any questions in the meantime, reach out to us anytime.</p>"
                + "<p style=\"margin:24px 0 0;\">Welcome to the Vikisol family - we're excited to have you on board!</p>"
                + signatureBlock("Warm regards", "Talent Acquisition Team");

        String html = brandedTemplate("Your offer from Vikisol is ready", body);
        if (offerLetterPdf != null) {
            String safeFileName = "Offer_Letter_" + candidateName.trim().replaceAll("\\s+", "_") + ".pdf";
            sendHtmlEmailWithAttachment(email, subject, html, new Attachment(safeFileName, offerLetterPdf), EmailLog.Category.OFFER, null);
        } else {
            sendHtmlEmail(email, subject, html, EmailLog.Category.OFFER, null);
        }
    }

    public void sendHikeLetterEmail(String email, String name, BigDecimal oldCtc, BigDecimal newCtc,
                                     Map<String, BigDecimal> ctcBreakup, LocalDate effectiveDate, String reason) {
        String subject = "Congratulations " + name + " - Your Salary Revision at Vikisol";

        StringBuilder breakupRows = new StringBuilder();
        if (ctcBreakup != null) {
            ctcBreakup.forEach((k, v) -> {
                if (!"ctc".equals(k)) {
                    String label = k.replaceAll("([A-Z])", " $1");
                    label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
                    breakupRows.append(rowHtml(label, "&#8377; " + v));
                }
            });
        }
        BigDecimal hikePct = oldCtc != null && oldCtc.compareTo(BigDecimal.ZERO) > 0
                ? newCtc.subtract(oldCtc).multiply(new BigDecimal("100")).divide(oldCtc, 1, RoundingMode.HALF_UP)
                : null;

        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Congratulations, " + name + "! &#127881;</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">We are pleased to inform you of a revision to your compensation at <b>Vikisol Technologies Pvt Ltd</b>, effective <b>" + effectiveDate + "</b>.</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + rowHtml("Previous Annual CTC", "&#8377; " + oldCtc)
                + rowHtml("New Annual CTC", "&#8377; " + newCtc)
                + (hikePct != null ? rowHtml("Hike", hikePct + "%") : "")
                + "</table>"
                + "<p style=\"margin:20px 0 8px;font-weight:600;\">New Monthly CTC Breakup</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + breakupRows
                + "</table>"
                + ((reason != null && !reason.isBlank()) ? "<p style=\"margin:0 0 20px;color:#444;\"><b>Note:</b> " + reason + "</p>" : "")
                + "<p style=\"margin:0;color:#444;\">Thank you for your continued contribution to Vikisol - this revision reflects the value you bring to the team.</p>"
                + signatureBlock("Warm regards", "Human Resources");

        sendHtmlEmail(email, subject, brandedTemplate("Your salary revision at Vikisol", body), EmailLog.Category.OTHER, null);
    }

    public void sendResignationAcknowledgementEmail(String email, String name, LocalDate lastWorkingDate) {
        String subject = "Resignation Acknowledged - " + name;

        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Resignation Acknowledged</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Dear " + name + ",</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">This is to acknowledge receipt of your resignation from Vikisol Technologies Pvt Ltd.</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + rowHtml("Last Working Day", String.valueOf(lastWorkingDate))
                + "</table>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Our HR team will reach out shortly regarding your exit formalities, full and final settlement, and knowledge transfer plan.</p>"
                + "<p style=\"margin:0;color:#444;\">We appreciate your contributions and wish you the very best for your future endeavors.</p>"
                + signatureBlock("Regards", "Human Resources");

        sendHtmlEmail(email, subject, brandedTemplate("Your resignation has been acknowledged", body), EmailLog.Category.EXIT, null);
    }

    // Farewell email sent as the final step of offboarding, once the exit package (whatever
    // documents actually exist for the employee) has been assembled - see OffboardingService.
    public void sendExitPackageEmail(String personalEmail, String name, List<Attachment> attachments) {
        String subject = "Farewell from Vikisol Technologies - " + name;
        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Farewell, " + name + "</h2>"
                + "<p style=\"margin:0 0 16px;color:#444;\">Dear " + name + ",</p>"
                + "<p style=\"margin:0 0 16px;color:#444;\">Thank you for being part of Vikisol Technologies.</p>"
                + "<p style=\"margin:0 0 16px;color:#444;\">Your dedication and contributions have helped us grow, and we truly appreciate everything you've done.</p>"
                + "<p style=\"margin:0 0 16px;color:#444;\">We wish you continued success in your future journey.</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\"><b>Once a Vikisol employee, Always a member of the Vikisol family.</b></p>"
                + signatureBlock("Warm Regards", "Human Resources");
        String html = brandedTemplate("Farewell from Vikisol Technologies", body);
        if (attachments != null && !attachments.isEmpty()) {
            sendHtmlEmailWithAttachments(personalEmail, subject, html, attachments, EmailLog.Category.EXIT, null);
        } else {
            sendHtmlEmail(personalEmail, subject, html, EmailLog.Category.EXIT, null);
        }
    }

    public void sendTimesheetSubmittedNotification(String managerEmail, String employeeName, String weekLabel) {
        String subject = "Timesheet Submitted for Approval - " + employeeName;
        String body = String.format("Dear Manager,\n\n%s has submitted their timesheet for %s and it is awaiting your approval.\n\nPlease log in to Vikisol One to review.\n\nRegards,\nVikisol One HR", employeeName, weekLabel);
        sendEmail(managerEmail, subject, body, EmailLog.Category.REMINDER, null);
    }

    // Sent to the employee's PERSONAL email (never official - they have no access to that inbox
    // until this activation link is used), with a one-time link instead of a temp password. No
    // password ever appears in an email under this flow.
    public void sendActivationEmail(String personalEmail, String name, String activationLink) {
        var rendered = emailTemplateService.render(TemplateKey.ACCOUNT_ACTIVATION,
                Map.of("name", name, "activationLink", activationLink));
        sendHtmlEmail(personalEmail, rendered.subject(), brandedTemplate("Activate your Vikisol One account", rendered.bodyHtml()), EmailLog.Category.WELCOME, null);
    }

    // Sent once activation actually completes (distinct from the activation invite above, which
    // is sent when the account is first created).
    public void sendWelcomeEmail(String officialEmail, String name) {
        var rendered = emailTemplateService.render(TemplateKey.WELCOME_EMAIL, Map.of("name", name));
        sendHtmlEmail(officialEmail, rendered.subject(), brandedTemplate("Welcome to Vikisol One", rendered.bodyHtml()), EmailLog.Category.WELCOME, null);
    }

    // OTP Login - a short numeric code, not a clicked link, so this is deliberately terse and
    // renders the code in large text. Sent to the OFFICIAL email address the employee typed on
    // the login screen (that's the account they're proving ownership of), unlike password-reset/
    // new-login-alert emails which go to the personal/recovery address.
    // Same self-invocation @Async gotcha as sendNewLoginAlertEmail - without this, requesting an
    // OTP code blocks on a live SMTP round-trip before the "code sent" response reaches the
    // frontend, and the OTP tab's countdown timer only starts once that response arrives.
    @Async
    public void sendLoginOtpEmail(String officialEmail, String name, String code, int validForSeconds) {
        String subject = "Your Vikisol One sign-in code: " + code;
        String validText = validForSeconds % 60 == 0 && validForSeconds >= 60
                ? (validForSeconds / 60) + " minute" + (validForSeconds / 60 == 1 ? "" : "s")
                : validForSeconds + " seconds";
        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Your sign-in code</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Dear " + name + ",</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Use this code to finish signing in to Vikisol One:</p>"
                + "<p style=\"margin:0 0 20px;text-align:center;font-size:36px;font-weight:700;letter-spacing:8px;color:#1a1a1a;\">" + code + "</p>"
                + "<p style=\"margin:0 0 16px;color:#444;\">This code expires in " + validText + ". If you didn't request this, you can safely ignore this email.</p>"
                + signatureBlock("Regards", "Vikisol One Security");
        sendHtmlEmail(officialEmail, subject, brandedTemplate("Your sign-in code", body), EmailLog.Category.OTHER, null);
    }

    public void sendPasswordChangedEmail(String officialEmail, String name, String changedAt) {
        var rendered = emailTemplateService.render(TemplateKey.PASSWORD_CHANGED,
                Map.of("name", name, "officialEmail", officialEmail, "changedAt", changedAt));
        sendHtmlEmail(officialEmail, rendered.subject(), brandedTemplate("Your password was changed", rendered.bodyHtml()), EmailLog.Category.OTHER, null);
    }

    public void sendAccountLockedEmail(String officialEmail, String name, int lockoutMinutes) {
        var rendered = emailTemplateService.render(TemplateKey.ACCOUNT_LOCKED,
                Map.of("name", name, "officialEmail", officialEmail, "lockoutMinutes", String.valueOf(lockoutMinutes)));
        sendHtmlEmail(officialEmail, rendered.subject(), brandedTemplate("Your account has been locked", rendered.bodyHtml()), EmailLog.Category.OTHER, null);
    }

    public void sendAccountUnlockedEmail(String officialEmail, String name) {
        var rendered = emailTemplateService.render(TemplateKey.ACCOUNT_UNLOCKED,
                Map.of("name", name, "officialEmail", officialEmail));
        sendHtmlEmail(officialEmail, rendered.subject(), brandedTemplate("Your account has been unlocked", rendered.bodyHtml()), EmailLog.Category.OTHER, null);
    }

    // Forgot Password - always sent to the employee's PERSONAL email, never the official company
    // mailbox, even though the employee identified themselves by official email on the Forgot
    // Password screen. officialEmail is shown in the email body purely as a reminder of what to
    // log back in with after resetting.
    public void sendPasswordResetEmail(String personalEmail, String name, String officialEmail, String resetLink) {
        var rendered = emailTemplateService.render(TemplateKey.PASSWORD_RESET,
                Map.of("name", name, "officialEmail", officialEmail, "resetLink", resetLink));
        sendHtmlEmail(personalEmail, rendered.subject(), brandedTemplate("Reset your Vikisol HRMS password", rendered.bodyHtml()), EmailLog.Category.OTHER, null);
    }

    public void sendAssessmentResultEmail(String email, String name, String testName, double score, double maxScore, boolean passed) {
        String subject = (passed ? "Congratulations " : "Your Result: ") + name + " - " + testName + " Assessment";
        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">" + (passed ? "Well done, " + name + "! &#127881;" : "Thank you, " + name) + "</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">You have completed the <b>" + testName + "</b> assessment on Vikisol Arena.</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + rowHtml("Score", score + " / " + maxScore)
                + rowHtml("Result", passed ? "PASSED" : "NOT SHORTLISTED")
                + "</table>"
                + "<p style=\"margin:0 0 20px;color:#444;\">"
                + (passed ? "Our recruitment team will reach out shortly to schedule your next round." : "We appreciate the time you invested and encourage you to apply again in the future.")
                + "</p>"
                + signatureBlock("Regards", "Talent Acquisition Team");
        sendHtmlEmail(email, subject, brandedTemplate("Your assessment result from Vikisol", body));
    }

    public void sendAssessmentNotificationEmail(String email, String candidateName, String candidateEmail, String testName,
                                                 double score, double maxScore, boolean passed) {
        String subject = "New Assessment Result - " + candidateName + " (" + (passed ? "PASS" : "FAIL") + ")";
        String body = String.format(
                "A candidate has completed an assessment on Vikisol Arena.\n\nCandidate: %s\nEmail: %s\nTest: %s\nScore: %s / %s\nResult: %s\n\nLog in to Vikisol One to view full details and move the candidate to interview.\n\nRegards,\nVikisol One",
                candidateName, candidateEmail, testName, score, maxScore, passed ? "PASS" : "FAIL");
        sendEmail(email, subject, body);
    }

}
