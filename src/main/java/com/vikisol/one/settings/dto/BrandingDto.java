package com.vikisol.one.settings.dto;

// Single source of truth every generated document (offer letters, payslips, certificates, etc.)
// pulls its logo/signature/company-info from, instead of each PDF builder hardcoding its own
// copy of "Vikisol Technologies Pvt Ltd" and a logo URL. Also backs the Company Branding admin
// page - every field here is editable there and takes effect on the next document generated,
// no redeploy needed.
public record BrandingDto(
        String companyName,
        String companyAddress,
        String gstNumber,
        String panNumber,
        String cinNumber,
        String website,
        String phone,
        String email,
        String logoUrl,
        String darkLogoUrl,
        String lightLogoUrl,
        String letterheadUrl,
        String footerText,
        String watermarkUrl,
        String ceoSignatureUrl,
        String hrSignatureUrl,
        String authorizedSignatoryUrl,
        String companySealUrl,
        String ceoName,
        String hrName,
        String primaryColor,
        String secondaryColor,
        String fontFamily,
        String defaultMargin,
        String tagline,
        // Company-wide document defaults (used by Offer/Appointment/Joining letters etc.) so
        // these aren't hardcoded per-caller - editable on the Company Branding admin page.
        String officeLocation,
        String workStartTime,
        String workEndTime,
        String workDays,
        String orientationContact
) {
}
