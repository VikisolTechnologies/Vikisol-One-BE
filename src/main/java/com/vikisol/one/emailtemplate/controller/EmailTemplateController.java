package com.vikisol.one.emailtemplate.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.emailtemplate.entity.EmailTemplate.TemplateKey;
import com.vikisol.one.emailtemplate.service.EmailTemplateService;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// CEO/Admin-only editing of the 6 authentication email templates (Security Center's Email
// Templates section). Content inherits the shared brand shell (logo/footer) at send time in
// EmailService - only the inner body/subject are editable here.
@RestController
@RequestMapping("/email-templates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CEO','ADMIN')")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Email templates retrieved", emailTemplateService.listAll()));
    }

    public record UpdateRequest(String subject, String bodyHtml) {}

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable("key") TemplateKey key,
                                                      @RequestBody UpdateRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        emailTemplateService.update(key, request.subject(), request.bodyHtml(), principal.getEmail());
        return ResponseEntity.ok(new ApiResponse<>(true, "Email template updated", null));
    }

    @PostMapping("/{key}/reset")
    public ResponseEntity<ApiResponse<Void>> resetToDefault(@PathVariable("key") TemplateKey key) {
        emailTemplateService.resetToDefault(key);
        return ResponseEntity.ok(new ApiResponse<>(true, "Email template reset to default", null));
    }
}
