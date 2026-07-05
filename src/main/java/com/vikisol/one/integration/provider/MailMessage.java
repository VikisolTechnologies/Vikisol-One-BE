package com.vikisol.one.integration.provider;

import java.util.List;

public record MailMessage(
        List<String> to,
        List<String> cc,
        String subject,
        String htmlBody,
        List<Attachment> attachments,
        // The mailbox to send *as* - when set and the provider supports send-as (e.g. Microsoft
        // Graph app-only /users/{id}/sendMail), the email comes from the actual employee's official
        // mailbox (e.g. the recruiter who scheduled it) instead of a shared system address.
        String sendAsEmail
) {
    public record Attachment(String filename, byte[] content) {}
}
