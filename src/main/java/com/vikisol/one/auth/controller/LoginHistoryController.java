package com.vikisol.one.auth.controller;

import com.vikisol.one.auth.dto.LoginHistoryResponse;
import com.vikisol.one.auth.repository.LoginHistoryEntryRepository;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/login-history")
@RequiredArgsConstructor
public class LoginHistoryController {

    private final LoginHistoryEntryRepository loginHistoryEntryRepository;

    // CEO/HR Manager/Admin only - security-wide login history across every employee.
    @GetMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<LoginHistoryResponse>>> getAll(
            @RequestParam(defaultValue = "100") int size) {
        List<LoginHistoryResponse> entries = loginHistoryEntryRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, size))
                .map(LoginHistoryResponse::from)
                .getContent();
        return ResponseEntity.ok(new ApiResponse<>(true, "Login history retrieved", entries));
    }

    // Any employee can see their OWN login history (no privilege check needed - it's scoped by
    // the authenticated principal's own email, not a path variable someone else could substitute).
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<LoginHistoryResponse>>> getMine(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(defaultValue = "50") int size) {
        List<LoginHistoryResponse> entries = loginHistoryEntryRepository
                .findByUserEmailOrderByCreatedAtDesc(principal.getEmail(), PageRequest.of(0, size))
                .map(LoginHistoryResponse::from)
                .getContent();
        return ResponseEntity.ok(new ApiResponse<>(true, "Login history retrieved", entries));
    }
}
