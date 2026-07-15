package com.vikisol.one.notification.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.notification.dto.NotificationPreferenceRequest;
import com.vikisol.one.notification.entity.Notification.Priority;
import com.vikisol.one.notification.service.NotificationService;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getMyNotifications(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notifications fetched",
                notificationService.getMyNotifications(principal, PageRequest.of(page, size))));
    }

    // Filtered/searchable list - used by the revamped Notification Center panel.
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<?>> searchMyNotifications(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size,
                                                                  @RequestParam(required = false) String category,
                                                                  @RequestParam(required = false) Priority priority,
                                                                  @RequestParam(required = false) Boolean read,
                                                                  @RequestParam(required = false) Boolean archived,
                                                                  @RequestParam(required = false) String search) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notifications fetched",
                notificationService.searchMyNotifications(principal, PageRequest.of(page, size),
                        category, priority, read, archived, search)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<?>> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Count fetched", notificationService.getUnreadCount(principal)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<?>> markAsRead(@PathVariable UUID id,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAsRead(id, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Marked as read", null));
    }

    @PutMapping("/read-bulk")
    public ResponseEntity<ApiResponse<?>> markManyAsRead(@RequestBody List<UUID> ids,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markManyAsRead(ids, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Marked as read", null));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<?>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllAsRead(principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "All marked as read", null));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<?>> archive(@PathVariable UUID id,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.setArchived(id, true, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Archived", null));
    }

    @PutMapping("/{id}/unarchive")
    public ResponseEntity<ApiResponse<?>> unarchive(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.setArchived(id, false, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Unarchived", null));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<?>> getMyPreferences(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Preferences fetched", notificationService.getMyPreferences(principal)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<?>> updateMyPreferences(@AuthenticationPrincipal UserPrincipal principal,
                                                               @RequestBody NotificationPreferenceRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Preferences updated", notificationService.updateMyPreferences(principal, request)));
    }
}
