package com.vikisol.one.notification.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.notification.service.NotificationService;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<?>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllAsRead(principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "All marked as read", null));
    }
}
