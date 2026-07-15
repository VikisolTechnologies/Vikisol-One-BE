package com.vikisol.one.notification.service;

import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.common.exception.ResourceNotFoundException;
import com.vikisol.one.notification.dto.CreateNotificationRequest;
import com.vikisol.one.notification.dto.NotificationPreferenceRequest;
import com.vikisol.one.notification.dto.NotificationPreferenceResponse;
import com.vikisol.one.notification.dto.NotificationResponse;
import com.vikisol.one.notification.entity.Notification;
import com.vikisol.one.notification.entity.Notification.NotificationType;
import com.vikisol.one.notification.entity.Notification.Priority;
import com.vikisol.one.notification.entity.NotificationPreference;
import com.vikisol.one.notification.repository.NotificationPreferenceRepository;
import com.vikisol.one.notification.repository.NotificationRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public void createNotification(CreateNotificationRequest request) {
        if (!isAllowed(request.recipientId(), request.type())) return;
        Notification n = new Notification();
        n.setRecipientId(request.recipientId());
        n.setTitle(request.title());
        n.setMessage(request.message());
        n.setType(request.type());
        n.setReferenceId(request.referenceId());
        n.setReferenceType(request.referenceType());
        n.setPriority(request.priority());
        n.setCategory(request.category());
        n.setDeepLink(request.deepLink());
        notificationRepository.save(n);
    }

    // No saved preference row yet -> default to sending, matching this app's behavior before
    // preferences existed at all (nobody's notifications silently start disappearing just because
    // they've never opened the Settings > Notifications tab).
    private boolean isAllowed(UUID recipientId, NotificationType type) {
        NotificationPreference p = notificationPreferenceRepository.findByUserId(recipientId).orElse(null);
        if (p == null) return true;
        return switch (type) {
            case LEAVE -> p.isLeaveReminders();
            case TIMESHEET -> p.isTimesheetReminders();
            case PAYROLL -> p.isPayrollAlerts();
            case RECRUITMENT -> p.isInterviewReminders();
            case ATTENDANCE, PERFORMANCE, GENERAL, PROJECT, OFFBOARDING, TICKET -> p.isPushNotifications();
        };
    }

    public NotificationPreferenceResponse getMyPreferences(UserPrincipal principal) {
        NotificationPreference p = notificationPreferenceRepository.findByUserId(principal.getId())
                .orElseGet(() -> NotificationPreference.builder().userId(principal.getId()).build());
        return mapPreference(p);
    }

    public NotificationPreferenceResponse updateMyPreferences(UserPrincipal principal, NotificationPreferenceRequest request) {
        NotificationPreference p = notificationPreferenceRepository.findByUserId(principal.getId())
                .orElseGet(() -> notificationPreferenceRepository.save(
                        NotificationPreference.builder().userId(principal.getId()).build()));
        if (request.emailNotifications() != null) p.setEmailNotifications(request.emailNotifications());
        if (request.pushNotifications() != null) p.setPushNotifications(request.pushNotifications());
        if (request.leaveReminders() != null) p.setLeaveReminders(request.leaveReminders());
        if (request.timesheetReminders() != null) p.setTimesheetReminders(request.timesheetReminders());
        if (request.birthdayReminders() != null) p.setBirthdayReminders(request.birthdayReminders());
        if (request.interviewReminders() != null) p.setInterviewReminders(request.interviewReminders());
        if (request.payrollAlerts() != null) p.setPayrollAlerts(request.payrollAlerts());
        return mapPreference(notificationPreferenceRepository.save(p));
    }

    private NotificationPreferenceResponse mapPreference(NotificationPreference p) {
        return new NotificationPreferenceResponse(p.isEmailNotifications(), p.isPushNotifications(),
                p.isLeaveReminders(), p.isTimesheetReminders(), p.isBirthdayReminders(),
                p.isInterviewReminders(), p.isPayrollAlerts());
    }

    public void sendNotification(UUID recipientId, String title, String message,
                                  NotificationType type, UUID refId, String refType) {
        createNotification(new CreateNotificationRequest(recipientId, title, message, type, refId, refType));
    }

    // Richer variant for callers that want to set priority/category/deepLink (e.g. offboarding
    // deep-linking to the case, or a HIGH priority urgent alert).
    public void sendNotification(UUID recipientId, String title, String message,
                                  NotificationType type, UUID refId, String refType,
                                  Priority priority, String category, String deepLink) {
        createNotification(new CreateNotificationRequest(recipientId, title, message, type, refId, refType,
                priority, category, deepLink));
    }

    public PagedResponse<NotificationResponse> getMyNotifications(UserPrincipal principal, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                principal.getId(), pageable);
        return new PagedResponse<>(
                page.getContent().stream().map(this::mapResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public PagedResponse<NotificationResponse> searchMyNotifications(UserPrincipal principal, Pageable pageable,
                                                                      String category, Priority priority,
                                                                      Boolean read, Boolean archived, String search) {
        Page<Notification> page = notificationRepository.search(
                principal.getId(), category, priority, read, archived,
                (search != null && !search.isBlank()) ? search.trim() : null, pageable);
        return new PagedResponse<>(
                page.getContent().stream().map(this::mapResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public long getUnreadCount(UserPrincipal principal) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(principal.getId());
    }

    public void markAsRead(UUID notificationId, UserPrincipal principal) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.setRead(true);
        n.setReadAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    public void markManyAsRead(java.util.List<UUID> ids, UserPrincipal principal) {
        ids.forEach(id -> markAsRead(id, principal));
    }

    public void markAllAsRead(UserPrincipal principal) {
        notificationRepository.findByRecipientIdAndIsReadFalse(principal.getId())
                .forEach(n -> {
                    n.setRead(true);
                    n.setReadAt(LocalDateTime.now());
                    notificationRepository.save(n);
                });
    }

    public void setArchived(UUID notificationId, boolean archived, UserPrincipal principal) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.setArchived(archived);
        notificationRepository.save(n);
    }

    private NotificationResponse mapResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getRecipientId(), n.getTitle(), n.getMessage(),
                n.getType().name(), n.getReferenceId(), n.getReferenceType(),
                n.isRead(), n.getReadAt(), n.getCreatedAt(),
                n.getPriority() != null ? n.getPriority().name() : null,
                n.getCategory(),
                Boolean.TRUE.equals(n.getArchived()),
                n.getDeepLink());
    }
}
