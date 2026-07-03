package com.vikisol.one.announcement.controller;

import com.vikisol.one.announcement.dto.AnnouncementRequest;
import com.vikisol.one.announcement.dto.AnnouncementResponse;
import com.vikisol.one.announcement.service.AnnouncementService;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    // Open to every authenticated role - company-wide announcements are meant to be seen by all.
    @GetMapping
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Announcements retrieved", announcementService.getAll()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @Valid @RequestBody AnnouncementRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Announcement posted", announcementService.create(request, principal)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Announcement updated", announcementService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Announcement removed", null));
    }
}
