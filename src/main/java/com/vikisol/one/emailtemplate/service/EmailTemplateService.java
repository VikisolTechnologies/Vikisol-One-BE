package com.vikisol.one.emailtemplate.service;

import com.vikisol.one.emailtemplate.entity.EmailTemplate;
import com.vikisol.one.emailtemplate.entity.EmailTemplate.TemplateKey;
import com.vikisol.one.emailtemplate.repository.EmailTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

// Backing store for the 6 authentication emails (activation, password reset, password changed,
// account locked/unlocked, welcome). Same pattern as BrandingService/AuthSettingsService: a
// hardcoded DEFAULTS map seeds sensible content, a DB row overrides it once an admin edits it via
// Security Center's Email Templates section. Placeholders are plain {{token}} substitution - no
// templating engine needed for this small, fixed set of variables.
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository repository;

    private static final Map<TemplateKey, String> DEFAULT_SUBJECTS = new EnumMap<>(TemplateKey.class);
    private static final Map<TemplateKey, String> DEFAULT_BODIES = new EnumMap<>(TemplateKey.class);

    static {
        DEFAULT_SUBJECTS.put(TemplateKey.ACCOUNT_ACTIVATION, "Welcome to Vikisol Technologies - Activate Your Account");
        DEFAULT_BODIES.put(TemplateKey.ACCOUNT_ACTIVATION,
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Welcome to Vikisol Technologies, {{name}}! &#127881;</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your account on <b>Vikisol One</b>, our HR platform, has been created. Click below to activate your account and set your own password.</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:20px;\">"
                + "<tr><td align=\"center\"><a href=\"{{activationLink}}\" style=\"display:inline-block;background:#FF6A00;color:#fff;text-decoration:none;font-weight:600;padding:12px 28px;border-radius:8px;\">Activate Account</a></td></tr></table>"
                + "<p style=\"margin:0 0 20px;color:#444;\">This link expires in 24 hours. If it expires, ask HR to resend your activation email.</p>"
                + "<p style=\"margin:28px 0 0;color:#1a1a1a;\">Regards,<br/><b>Human Resources</b><br/>Vikisol Technologies Pvt Ltd</p>");

        DEFAULT_SUBJECTS.put(TemplateKey.PASSWORD_RESET, "Reset Your Vikisol HRMS Password");
        DEFAULT_BODIES.put(TemplateKey.PASSWORD_RESET,
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Reset Your Vikisol HRMS Password</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Hello {{name}},</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">We received a request to reset the password for your Vikisol HRMS account (<b>{{officialEmail}}</b>).</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:20px;\">"
                + "<tr><td align=\"center\"><a href=\"{{resetLink}}\" style=\"display:inline-block;background:#FF6A00;color:#fff;text-decoration:none;font-weight:600;padding:12px 28px;border-radius:8px;\">Reset Password</a></td></tr></table>"
                + "<p style=\"margin:0 0 8px;color:#444;\">This link expires in 30 minutes.</p>"
                + "<p style=\"margin:0 0 20px;color:#888;font-size:13px;\">If you did not request this, you can safely ignore this email - your password will not be changed.</p>"
                + "<p style=\"margin:28px 0 0;color:#1a1a1a;\">Regards,<br/><b>Human Resources</b><br/>Vikisol Technologies Pvt Ltd</p>");

        DEFAULT_SUBJECTS.put(TemplateKey.PASSWORD_CHANGED, "Your Vikisol HRMS Password Was Changed");
        DEFAULT_BODIES.put(TemplateKey.PASSWORD_CHANGED,
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Password Changed</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Hello {{name}},</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">This confirms the password for your Vikisol HRMS account (<b>{{officialEmail}}</b>) was changed on {{changedAt}}. You have been signed out of all devices for your security.</p>"
                + "<p style=\"margin:0 0 20px;color:#888;font-size:13px;\">If you did not make this change, contact HR/IT immediately.</p>"
                + "<p style=\"margin:28px 0 0;color:#1a1a1a;\">Regards,<br/><b>Human Resources</b><br/>Vikisol Technologies Pvt Ltd</p>");

        DEFAULT_SUBJECTS.put(TemplateKey.ACCOUNT_LOCKED, "Your Vikisol HRMS Account Has Been Locked");
        DEFAULT_BODIES.put(TemplateKey.ACCOUNT_LOCKED,
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Account Locked</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Hello {{name}},</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your Vikisol HRMS account (<b>{{officialEmail}}</b>) has been temporarily locked after too many failed login attempts. It will automatically unlock in {{lockoutMinutes}} minutes, or an administrator can unlock it sooner.</p>"
                + "<p style=\"margin:0 0 20px;color:#888;font-size:13px;\">If this wasn't you, contact HR/IT immediately.</p>"
                + "<p style=\"margin:28px 0 0;color:#1a1a1a;\">Regards,<br/><b>Human Resources</b><br/>Vikisol Technologies Pvt Ltd</p>");

        DEFAULT_SUBJECTS.put(TemplateKey.ACCOUNT_UNLOCKED, "Your Vikisol HRMS Account Has Been Unlocked");
        DEFAULT_BODIES.put(TemplateKey.ACCOUNT_UNLOCKED,
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Account Unlocked</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Hello {{name}},</p>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your Vikisol HRMS account (<b>{{officialEmail}}</b>) has been unlocked. You can now sign in again.</p>"
                + "<p style=\"margin:28px 0 0;color:#1a1a1a;\">Regards,<br/><b>Human Resources</b><br/>Vikisol Technologies Pvt Ltd</p>");

        DEFAULT_SUBJECTS.put(TemplateKey.WELCOME_EMAIL, "Welcome Aboard, {{name}}!");
        DEFAULT_BODIES.put(TemplateKey.WELCOME_EMAIL,
                "<h2 style=\"margin:0 0 4px;font-size:20px;\">Welcome Aboard, {{name}}! &#127881;</h2>"
                + "<p style=\"margin:0 0 20px;color:#444;\">Your Vikisol One account is now active. We're excited to have you as part of the team.</p>"
                + "<p style=\"margin:28px 0 0;color:#1a1a1a;\">Warm regards,<br/><b>Human Resources</b><br/>Vikisol Technologies Pvt Ltd</p>");
    }

    public record RenderedEmail(String subject, String bodyHtml) {}

    public RenderedEmail render(TemplateKey key, Map<String, String> vars) {
        EmailTemplate row = repository.findByTemplateKey(key).orElse(null);
        String subject = row != null ? row.getSubject() : DEFAULT_SUBJECTS.get(key);
        String body = row != null ? row.getBodyHtml() : DEFAULT_BODIES.get(key);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String token = "{{" + e.getKey() + "}}";
            String value = e.getValue() == null ? "" : e.getValue();
            subject = subject.replace(token, value);
            body = body.replace(token, value);
        }
        return new RenderedEmail(subject, body);
    }

    public java.util.List<Map<String, String>> listAll() {
        return java.util.Arrays.stream(TemplateKey.values()).map(key -> {
            EmailTemplate row = repository.findByTemplateKey(key).orElse(null);
            Map<String, String> m = new java.util.HashMap<>();
            m.put("templateKey", key.name());
            m.put("subject", row != null ? row.getSubject() : DEFAULT_SUBJECTS.get(key));
            m.put("bodyHtml", row != null ? row.getBodyHtml() : DEFAULT_BODIES.get(key));
            m.put("customized", row != null ? "true" : "false");
            return m;
        }).toList();
    }

    public void update(TemplateKey key, String subject, String bodyHtml, String updatedByEmail) {
        EmailTemplate row = repository.findByTemplateKey(key).orElseGet(() -> EmailTemplate.builder().templateKey(key).build());
        row.setSubject(subject);
        row.setBodyHtml(bodyHtml);
        row.setUpdatedByEmail(updatedByEmail);
        repository.save(row);
    }

    public void resetToDefault(TemplateKey key) {
        repository.findByTemplateKey(key).ifPresent(repository::delete);
    }
}
