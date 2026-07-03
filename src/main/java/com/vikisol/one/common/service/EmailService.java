package com.vikisol.one.common.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:careers@vikisol.in}")
    private String fromEmail;

    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("HTML email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    /** Wraps body content in Vikisol's black/white/orange brand shell. */
    private String brandedTemplate(String preheader, String bodyHtml) {
        return "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f4f5;font-family:Segoe UI,Helvetica,Arial,sans-serif;\">"
                + "<span style=\"display:none;max-height:0;overflow:hidden;\">" + preheader + "</span>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);\">"
                + "<tr><td style=\"background:#0a0a0a;padding:28px 32px;\">"
                + "<span style=\"color:#ffffff;font-size:22px;font-weight:700;letter-spacing:4px;\">VIKI<span style=\"color:#FF6A00;\">S</span>OL</span>"
                + "<div style=\"height:2px;width:36px;background:#FF6A00;margin-top:8px;\"></div>"
                + "<div style=\"color:#9a9a9a;font-size:10px;letter-spacing:2px;margin-top:6px;\">TECHNOLOGY &nbsp;&bull;&nbsp; TALENT &nbsp;&bull;&nbsp; TRANSFORMATION</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:32px;color:#1a1a1a;font-size:14px;line-height:1.6;\">" + bodyHtml + "</td></tr>"
                + "<tr><td style=\"background:#f4f4f5;padding:20px 32px;color:#777;font-size:11px;text-align:center;\">"
                + "Vikisol Technologies Pvt Ltd &middot; careers@vikisol.in &middot; www.vikisol.in"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String rowHtml(String label, String value) {
        return "<tr><td style=\"padding:6px 0;color:#666;font-size:13px;\">" + label + "</td>"
                + "<td style=\"padding:6px 0;color:#1a1a1a;font-size:13px;font-weight:600;text-align:right;\">" + value + "</td></tr>";
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
                                      BigDecimal annualCtc, Map<String, BigDecimal> ctcBreakup, LocalDate dateOfJoining) {
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
                + "<p style=\"margin:0 0 20px;\">We are pleased to offer you the position of <b>" + designation + "</b>, reporting into the Vikisol leadership team, based on your experience and the discussions we've had with you.</p>"
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
                + "<p style=\"margin:0 0 20px;color:#444;\">Our HR team will be in touch shortly with your formal offer letter, onboarding checklist, and the list of documents required before your joining date. If you have any questions in the meantime, reach out to us anytime.</p>"
                + "<p style=\"margin:24px 0 0;\">Welcome to the Vikisol family - we're excited to have you on board!</p>"
                + "<p style=\"margin:16px 0 0;color:#444;\">Warm regards,<br/><b>Talent Acquisition Team</b><br/>Vikisol Technologies Pvt Ltd</p>";

        sendHtmlEmail(email, subject, brandedTemplate("Your offer from Vikisol is ready", body));
    }

    public void sendHikeLetterEmail(String email, String name, BigDecimal oldCtc, BigDecimal newCtc,
                                     Map<String, BigDecimal> ctcBreakup, LocalDate effectiveDate, String reason) {
        String subject = "Congratulations " + name + " - Your Salary Revision at Vikisol";
        StringBuilder breakupText = new StringBuilder();
        if (ctcBreakup != null) {
            ctcBreakup.forEach((k, v) -> {
                if (!"ctc".equals(k)) breakupText.append(String.format("  %-22s: Rs. %s%n", k, v));
            });
        }
        BigDecimal hikePct = oldCtc != null && oldCtc.compareTo(BigDecimal.ZERO) > 0
                ? newCtc.subtract(oldCtc).multiply(new BigDecimal("100")).divide(oldCtc, 1, RoundingMode.HALF_UP)
                : null;
        String body = String.format(
                "Dear %s,%n%nCongratulations! We are pleased to inform you of a revision to your compensation, effective %s.%n%n" +
                "Previous Annual CTC: Rs. %s%n" +
                "New Annual CTC: Rs. %s%n" +
                (hikePct != null ? "Hike: %s%%%n%n" : "%n") +
                "New Monthly Salary Breakup:%n%s%n" +
                "%s" +
                "Thank you for your continued contribution to Vikisol.%n%n" +
                "Regards,%nVikisol One HR",
                name, effectiveDate,
                oldCtc, newCtc,
                hikePct != null ? hikePct : "",
                breakupText,
                (reason != null && !reason.isBlank()) ? "Note: " + reason + "\n\n" : "");
        sendEmail(email, subject, body);
    }

    public void sendResignationAcknowledgementEmail(String email, String name, LocalDate lastWorkingDate) {
        String subject = "Resignation Acknowledged - " + name;
        String body = String.format(
                "Dear %s,%n%nThis is to acknowledge receipt of your resignation. Your last working day with Vikisol will be %s.%n%n" +
                "Our HR team will reach out shortly regarding your exit formalities, full and final settlement, and knowledge transfer plan.%n%n" +
                "We appreciate your contributions and wish you the very best for your future endeavors.%n%n" +
                "Regards,%nVikisol One HR",
                name, lastWorkingDate);
        sendEmail(email, subject, body);
    }

    public void sendTimesheetSubmittedNotification(String managerEmail, String employeeName, String weekLabel) {
        String subject = "Timesheet Submitted for Approval - " + employeeName;
        String body = String.format("Dear Manager,\n\n%s has submitted their timesheet for %s and it is awaiting your approval.\n\nPlease log in to Vikisol One to review.\n\nRegards,\nVikisol One HR", employeeName, weekLabel);
        sendEmail(managerEmail, subject, body);
    }

    public void sendWelcomeEmail(String email, String name, String tempPassword) {
        String subject = "Welcome to Vikisol One - Your Login Details";
        String body =
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Welcome aboard, " + name + "! &#127881;</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your account on <b>Vikisol One</b>, our HR platform, has been created. You can use it to view your payslips, apply for leave, log timesheets, and more.</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8f8f8;border-radius:8px;padding:16px;margin-bottom:20px;\">"
                + rowHtml("Login Email", email)
                + rowHtml("Temporary Password", tempPassword)
                + "</table>"
                + "<p style=\"margin:0 0 20px;color:#444;\">For security, please log in and change your password as soon as possible.</p>"
                + "<p style=\"margin:16px 0 0;color:#444;\">Regards,<br/><b>Vikisol One HR</b></p>";
        sendHtmlEmail(email, subject, brandedTemplate("Your Vikisol One account is ready", body));
    }
}
