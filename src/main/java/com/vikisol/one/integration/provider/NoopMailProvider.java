package com.vikisol.one.integration.provider;

import com.vikisol.one.common.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// Default MailProvider - routes through the existing Resend-backed EmailService, so every
// existing email flow (leave/payroll/ticket notifications, offer letters, interview invites
// before Microsoft 365 is configured) keeps working exactly as it did before this abstraction
// layer was introduced.
@Component
@RequiredArgsConstructor
public class NoopMailProvider implements MailProvider {

    private final EmailService emailService;

    @Override
    public String getProviderName() {
        return "Resend (default)";
    }

    @Override
    public boolean isConfigured() {
        return true; // Resend is always available as the baseline - "not configured" only applies to premium providers like Microsoft 365
    }

    @Override
    public void sendMail(MailMessage message) {
        List<EmailService.Attachment> attachments = message.attachments() == null ? List.of()
                : message.attachments().stream().map(a -> new EmailService.Attachment(a.filename(), a.content())).toList();
        emailService.sendRaw(message.to(), message.cc(), message.subject(), message.htmlBody(), attachments);
    }
}
