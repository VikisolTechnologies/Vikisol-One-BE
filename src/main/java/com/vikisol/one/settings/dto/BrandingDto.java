package com.vikisol.one.settings.dto;

// Single source of truth every generated document (offer letters, payslips, certificates, etc.)
// pulls its logo/signature/company-info from, instead of each PDF builder hardcoding its own
// copy of "Vikisol Technologies Pvt Ltd" and a logo URL.
public record BrandingDto(
        String companyName,
        String companyAddress,
        String gstNumber,
        String panNumber,
        String website,
        String phone,
        String email,
        String logoUrl,
        String ceoSignatureUrl,
        String hrSignatureUrl,
        String companySealUrl,
        String ceoName,
        String hrName
) {
}
