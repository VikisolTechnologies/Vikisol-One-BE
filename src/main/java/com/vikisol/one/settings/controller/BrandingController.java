package com.vikisol.one.settings.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.settings.dto.BrandingDto;
import com.vikisol.one.settings.service.BrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// CEO Dashboard's branding settings - logo, signatures, company info - that every generated
// HR document (offer letters, payslips, certificates) automatically inherits.
@RestController
@RequestMapping("/branding")
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService brandingService;

    @GetMapping
    public ResponseEntity<ApiResponse<BrandingDto>> getBranding() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Branding retrieved", brandingService.getBranding()));
    }

    // Keys: COMPANY_NAME, COMPANY_ADDRESS, GST, PAN, WEBSITE, PHONE, EMAIL, LOGO_URL,
    // CEO_SIGNATURE_URL, HR_SIGNATURE_URL, COMPANY_SEAL_URL, CEO_NAME, HR_NAME
    @PutMapping
    @PreAuthorize("hasAnyRole('CEO','ADMIN')")
    public ResponseEntity<ApiResponse<BrandingDto>> updateBranding(@RequestBody Map<String, String> fields) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Branding updated", brandingService.updateBranding(fields)));
    }
}
