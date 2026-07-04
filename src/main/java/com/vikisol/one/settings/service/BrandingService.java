package com.vikisol.one.settings.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.settings.dto.BrandingDto;
import com.vikisol.one.settings.entity.CompanySettings;
import com.vikisol.one.settings.repository.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

// Every generated HR document (offer letters, payslips, certificates...) reads its logo,
// signatures, and company info from here rather than each PDF template hardcoding its own copy.
// Stored as CompanySettings rows (category GENERAL) so the CEO can change them at runtime without
// a redeploy - defaults below only apply until a value is explicitly set.
@Service
@RequiredArgsConstructor
public class BrandingService {

    private final CompanySettingsRepository settingsRepository;
    private final AuditService auditService;

    @Value("${app.logo-url:https://res.cloudinary.com/drqgvncx1/image/upload/v1781621022/snipped_fccgpm.png}")
    private String defaultLogoUrl;

    @Value("${resend.support-email:connect@vikisol.in}")
    private String defaultSupportEmail;

    private static final Map<String, String> DEFAULTS = Map.of(
            "BRANDING_COMPANY_NAME", "Vikisol Technologies Pvt Ltd",
            "BRANDING_COMPANY_ADDRESS", "Bengaluru, Karnataka, India",
            "BRANDING_WEBSITE", "www.vikisol.in",
            "BRANDING_CEO_NAME", "Syam Prabhakar Seeli",
            "BRANDING_HR_NAME", "HR Team"
    );

    public BrandingDto getBranding() {
        return new BrandingDto(
                get("BRANDING_COMPANY_NAME", DEFAULTS.get("BRANDING_COMPANY_NAME")),
                get("BRANDING_COMPANY_ADDRESS", DEFAULTS.get("BRANDING_COMPANY_ADDRESS")),
                get("BRANDING_GST", ""),
                get("BRANDING_PAN", ""),
                get("BRANDING_CIN", ""),
                get("BRANDING_WEBSITE", DEFAULTS.get("BRANDING_WEBSITE")),
                get("BRANDING_PHONE", ""),
                get("BRANDING_EMAIL", defaultSupportEmail),
                get("BRANDING_LOGO_URL", defaultLogoUrl),
                get("BRANDING_DARK_LOGO_URL", ""),
                get("BRANDING_LIGHT_LOGO_URL", ""),
                get("BRANDING_LETTERHEAD_URL", ""),
                get("BRANDING_FOOTER_TEXT", ""),
                get("BRANDING_WATERMARK_URL", ""),
                get("BRANDING_CEO_SIGNATURE_URL", ""),
                get("BRANDING_HR_SIGNATURE_URL", ""),
                get("BRANDING_AUTHORIZED_SIGNATORY_URL", ""),
                get("BRANDING_COMPANY_SEAL_URL", ""),
                get("BRANDING_CEO_NAME", DEFAULTS.get("BRANDING_CEO_NAME")),
                get("BRANDING_HR_NAME", DEFAULTS.get("BRANDING_HR_NAME")),
                get("BRANDING_PRIMARY_COLOR", "#FF6B35"),
                get("BRANDING_SECONDARY_COLOR", "#0a0a0a"),
                get("BRANDING_FONT_FAMILY", "Helvetica, Arial, sans-serif"),
                get("BRANDING_DEFAULT_MARGIN", "36px 40px")
        );
    }

    public BrandingDto updateBranding(Map<String, String> fields) {
        fields.forEach((key, value) -> {
            String settingKey = "BRANDING_" + key;
            CompanySettings settings = settingsRepository.findByKey(settingKey)
                    .orElse(CompanySettings.builder()
                            .key(settingKey)
                            .category(CompanySettings.SettingsCategory.GENERAL)
                            .build());
            settings.setValue(value);
            settings.setDescription("Document branding: " + key);
            settingsRepository.save(settings);
        });
        auditService.record("Branding Updated", "Company Branding", "Fields changed: " + fields.keySet());
        return getBranding();
    }

    private String get(String key, String fallback) {
        return settingsRepository.findByKey(key).map(CompanySettings::getValue).filter(v -> !v.isBlank()).orElse(fallback);
    }
}
