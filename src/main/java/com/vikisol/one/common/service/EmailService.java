package com.vikisol.one.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured");
        }
        Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                "from", fromEmail,
                "to", List.of(to),
                "subject", subject,
                "html", htmlBody));
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

    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            send(to, subject, "<p style=\"white-space:pre-wrap;font-family:Segoe UI,Arial,sans-serif;\">" + body + "</p>");
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    // Synchronous (not @Async) so callers get a real success/failure result back, for email diagnostics.
    public void sendTestEmail(String to) throws Exception {
        send(to, "Vikisol One - Email delivery test",
                brandedTemplate("Email connectivity test",
                        "<p style=\"color:#333;font-size:14px;\">This is a test email confirming " + fromEmail + " is correctly wired up to Vikisol One via Resend.</p>"));
    }

    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            send(to, subject, htmlBody);
            log.info("HTML email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendHtmlEmailWithAttachment(String to, String subject, String htmlBody, Attachment attachment) {
        try {
            send(to, subject, htmlBody, List.of(attachment));
            log.info("HTML email with attachment sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send HTML email with attachment to {}: {}", to, e.getMessage());
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

    public void sendLeaveApprovalNotification(String employeeEmail, String employeeName, String status, String leaveType, String dates) {
        String subject = "Leave " + status + " - " + leaveType;
        String body = String.format("Dear %s,\n\nYour %s request for %s has been %s.\n\nRegards,\nVikisol One HR", employeeName, leaveType, dates, status.toLowerCase());
        sendEmail(employeeEmail, subject, body);
    }

    public void sendLeaveApplicationNotification(String managerEmail, String employeeName, String leaveType, String dates) {
        String subject = "Leave Application - " + employeeName;
        String body = String.format("Dear Manager,\n\n%s has applied for %s from %s.\nPlease review and take action.\n\nRegards,\nVikisol One HR", employeeName, leaveType, dates);
        sendEmail(managerEmail, subject, body);
    }

    public void sendPayrollProcessedNotification(String email, String name, String month, String year) {
        String subject = "Payslip Generated - " + month + "/" + year;
        String body = String.format("Dear %s,\n\nYour payslip for %s/%s has been generated. Please log in to view details.\n\nRegards,\nVikisol One HR", name, month, year);
        sendEmail(email, subject, body);
    }

    public void sendTicketNotification(String email, String ticketNumber, String title, String status) {
        String subject = "Ticket " + ticketNumber + " - " + status;
        String body = String.format("Ticket %s: %s\nStatus: %s\n\nPlease log in for details.\n\nRegards,\nVikisol One", ticketNumber, title, status);
        sendEmail(email, subject, body);
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
            sendHtmlEmailWithAttachment(email, subject, html, new Attachment(safeFileName, offerLetterPdf));
        } else {
            sendHtmlEmail(email, subject, html);
        }
    }

    /** Builds the standalone A4 offer-letter document (for the PDF attachment) - separate from the shorter notification email above. */
    public String buildOfferLetterPdfHtml(String candidateName, String employeeId, String designation,
                                           BigDecimal annualCtc, Map<String, BigDecimal> ctcBreakup,
                                           LocalDate dateOfJoining, String reportingManagerName, LocalDate issueDate) {
        StringBuilder breakupRows = new StringBuilder();
        if (ctcBreakup != null) {
            ctcBreakup.forEach((k, v) -> {
                if (!"ctc".equals(k)) {
                    String label = k.replaceAll("([A-Z])", " $1");
                    label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
                    breakupRows.append("<tr><td style=\"padding:5px 0;color:#444;font-size:11px;border-bottom:1px solid #eee;\">" + label + "</td>"
                            + "<td style=\"padding:5px 0;color:#111;font-size:11px;font-weight:bold;text-align:right;border-bottom:1px solid #eee;\">Rs. " + v + "</td></tr>");
                }
            });
        }
        LocalDate expiry = issueDate.plusDays(7);

        return "<html><head><meta charset=\"UTF-8\"/></head>"
                + "<body style=\"margin:0;padding:0;font-family:Helvetica,Arial,sans-serif;color:#1a1a1a;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"background:#0a0a0a;padding:24px 40px;\">"
                + "<img src=\"" + logoUrl + "\" alt=\"Vikisol\" style=\"height:34px;\"/>"
                + "<div style=\"color:#9a9a9a;font-size:9px;letter-spacing:2px;margin-top:8px;\">TECHNOLOGY &#8226; TALENT &#8226; TRANSFORMATION</div>"
                + "</td></tr></table>"
                + "<div style=\"padding:36px 40px;\">"
                + "<h1 style=\"font-size:18px;letter-spacing:1px;text-align:center;margin:0 0 4px;\">OFFER OF EMPLOYMENT</h1>"
                + "<p style=\"text-align:center;color:#777;font-size:11px;margin:0 0 28px;\">Date: " + issueDate + "</p>"
                + "<p style=\"font-size:12px;line-height:1.7;margin:0 0 4px;\">To,</p>"
                + "<p style=\"font-size:12px;line-height:1.7;font-weight:bold;margin:0 0 20px;\">" + candidateName + "</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">Dear " + candidateName + ",</p>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 16px;\">We are pleased to offer you employment with <b>Vikisol Technologies Pvt Ltd</b> ('the Company') as <b>" + designation + "</b>"
                + (reportingManagerName != null && !reportingManagerName.isBlank() ? ", reporting to <b>" + reportingManagerName + "</b>," : ",")
                + " on the terms set out below.</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:6px;padding:14px 18px;margin:0 0 20px;\">"
                + "<tr><td colspan=\"2\" style=\"padding-bottom:8px;\"><b style=\"font-size:12px;\">Employment Terms</b></td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;padding:3px 0;\">Employee ID</td><td style=\"font-size:11px;font-weight:bold;text-align:right;\">" + employeeId + "</td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;padding:3px 0;\">Designation</td><td style=\"font-size:11px;font-weight:bold;text-align:right;\">" + designation + "</td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;padding:3px 0;\">Date of Joining</td><td style=\"font-size:11px;font-weight:bold;text-align:right;\">" + dateOfJoining + "</td></tr>"
                + "<tr><td style=\"font-size:11px;color:#666;padding:3px 0;\">Annual CTC</td><td style=\"font-size:11px;font-weight:bold;text-align:right;\">Rs. " + annualCtc + "</td></tr>"
                + "</table>"
                + "<p style=\"font-size:12px;font-weight:bold;margin:0 0 8px;\">Monthly CTC Breakup</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;\">" + breakupRows + "</table>"
                + "<p style=\"font-size:12px;font-weight:bold;margin:0 0 8px;\">Terms &amp; Conditions</p>"
                + "<ol style=\"font-size:11px;line-height:1.8;color:#333;margin:0 0 20px;padding-left:18px;\">"
                + "<li>This offer is contingent upon successful completion of your background verification and submission of required documents.</li>"
                + "<li>You will be on probation for a period as communicated by HR, during which either party may terminate employment as per Company policy.</li>"
                + "<li>Your compensation and benefits are governed by the Company's HR policies, which may be revised from time to time.</li>"
                + "<li>You are expected to maintain confidentiality of the Company's proprietary and business information during and after your employment.</li>"
                + "<li>This offer is valid for acceptance until <b>" + expiry + "</b>, after which it stands withdrawn unless extended in writing by the Company.</li>"
                + "</ol>"
                + "<p style=\"font-size:12px;line-height:1.8;margin:0 0 32px;\">Please sign and return a copy of this letter to indicate your acceptance of these terms. We look forward to welcoming you to the Vikisol family.</p>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td width=\"50%\" style=\"font-size:11px;\">For <b>Vikisol Technologies Pvt Ltd</b><br/><br/><br/>_____________________<br/>Authorized Signatory</td>"
                + "<td width=\"50%\" style=\"font-size:11px;\">Accepted by<br/><br/><br/>_____________________<br/>" + candidateName + "</td></tr>"
                + "</table>"
                + "</div>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"background:#f4f4f5;padding:16px 40px;color:#888;font-size:9px;text-align:center;\">"
                + "Vikisol Technologies Pvt Ltd &#183; " + supportEmail + " &#183; www.vikisol.in"
                + "</td></tr></table>"
                + "</body></html>";
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

        sendHtmlEmail(email, subject, brandedTemplate("Your salary revision at Vikisol", body));
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

        sendHtmlEmail(email, subject, brandedTemplate("Your resignation has been acknowledged", body));
    }

    public void sendTimesheetSubmittedNotification(String managerEmail, String employeeName, String weekLabel) {
        String subject = "Timesheet Submitted for Approval - " + employeeName;
        String body = String.format("Dear Manager,\n\n%s has submitted their timesheet for %s and it is awaiting your approval.\n\nPlease log in to Vikisol One to review.\n\nRegards,\nVikisol One HR", employeeName, weekLabel);
        sendEmail(managerEmail, subject, body);
    }

    // Sent to the employee's PERSONAL email (never official - they have no access to that inbox
    // until this activation link is used), with a one-time link instead of a temp password. No
    // password ever appears in an email under this flow.
    public void sendActivationEmail(String personalEmail, String name, String activationLink) {
        String subject = "Welcome to Vikisol Technologies - Activate Your Account";
        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Welcome to Vikisol Technologies, " + name + "! &#127881;</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your account on <b>Vikisol One</b>, our HR platform, has been created. Click below to activate your account and set your own password.</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:20px;\">"
                + "<tr><td align=\"center\">"
                + "<a href=\"" + activationLink + "\" style=\"display:inline-block;background:#FF6A00;color:#fff;text-decoration:none;font-weight:600;padding:12px 28px;border-radius:8px;\">Activate Account</a>"
                + "</td></tr></table>"
                + "<p style=\"margin:0 0 20px;color:#444;\">This link expires in 24 hours. If it expires, ask HR to resend your activation email.</p>"
                + signatureBlock("Regards", "Human Resources");
        sendHtmlEmail(personalEmail, subject, brandedTemplate("Activate your Vikisol One account", body));
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
