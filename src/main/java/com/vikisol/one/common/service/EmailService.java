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

    public void sendWelcomeEmail(String email, String name, String tempPassword) {
        String subject = "Welcome to Vikisol One";
        String body = String.format("Dear %s,\n\nWelcome to Vikisol One!\n\nYour login credentials:\nEmail: %s\nPassword: %s\n\nPlease change your password after first login.\n\nRegards,\nVikisol One HR", name, email, tempPassword);
        sendEmail(email, subject, body);
    }
}
