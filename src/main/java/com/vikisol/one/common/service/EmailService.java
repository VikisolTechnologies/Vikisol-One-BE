package com.vikisol.one.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@vikisol.in}")
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
                                      java.math.BigDecimal annualCtc, java.util.Map<String, java.math.BigDecimal> ctcBreakup,
                                      java.time.LocalDate dateOfJoining) {
        String subject = "Congratulations " + candidateName + " - Offer of Employment at Vikisol";
        StringBuilder breakupText = new StringBuilder();
        if (ctcBreakup != null) {
            ctcBreakup.forEach((k, v) -> {
                if (!"ctc".equals(k)) breakupText.append(String.format("  %-22s: Rs. %s%n", k, v));
            });
        }
        String body = String.format(
                "Dear %s,%n%nCongratulations! We are delighted to offer you the position of %s at Vikisol.%n%n" +
                "Your Employee ID: %s%n" +
                "Annual CTC: Rs. %s%n" +
                "Date of Joining: %s%n%n" +
                "Monthly Salary Breakup:%n%s%n" +
                "Welcome to the Vikisol family! Our HR team will reach out with the formal offer letter and onboarding details.%n%n" +
                "Regards,%nVikisol One HR%ncareers@vikisol.in",
                candidateName, designation, employeeId, annualCtc, dateOfJoining, breakupText);
        sendEmail(email, subject, body);
    }

    public void sendHikeLetterEmail(String email, String name, java.math.BigDecimal oldCtc, java.math.BigDecimal newCtc,
                                     java.util.Map<String, java.math.BigDecimal> ctcBreakup, java.time.LocalDate effectiveDate, String reason) {
        String subject = "Congratulations " + name + " - Your Salary Revision at Vikisol";
        StringBuilder breakupText = new StringBuilder();
        if (ctcBreakup != null) {
            ctcBreakup.forEach((k, v) -> {
                if (!"ctc".equals(k)) breakupText.append(String.format("  %-22s: Rs. %s%n", k, v));
            });
        }
        java.math.BigDecimal hikePct = oldCtc != null && oldCtc.compareTo(java.math.BigDecimal.ZERO) > 0
                ? newCtc.subtract(oldCtc).multiply(new java.math.BigDecimal("100")).divide(oldCtc, 1, java.math.RoundingMode.HALF_UP)
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

    public void sendResignationAcknowledgementEmail(String email, String name, java.time.LocalDate lastWorkingDate) {
        String subject = "Resignation Acknowledged - " + name;
        String body = String.format(
                "Dear %s,%n%nThis is to acknowledge receipt of your resignation. Your last working day with Vikisol will be %s.%n%n" +
                "Our HR team will reach out shortly regarding your exit formalities, full and final settlement, and knowledge transfer plan.%n%n" +
                "We appreciate your contributions and wish you the very best for your future endeavors.%n%n" +
                "Regards,%nVikisol One HR",
                name, lastWorkingDate);
        sendEmail(email, subject, body);
    }

    public void sendWelcomeEmail(String email, String name, String tempPassword) {
        String subject = "Welcome to Vikisol One";
        String body = String.format("Dear %s,\n\nWelcome to Vikisol One!\n\nYour login credentials:\nEmail: %s\nPassword: %s\n\nPlease change your password after first login.\n\nRegards,\nVikisol One HR", name, email, tempPassword);
        sendEmail(email, subject, body);
    }
}
