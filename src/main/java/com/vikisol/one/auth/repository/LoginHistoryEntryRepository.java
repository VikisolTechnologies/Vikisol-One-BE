package com.vikisol.one.auth.repository;

import com.vikisol.one.auth.entity.LoginHistoryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LoginHistoryEntryRepository extends JpaRepository<LoginHistoryEntry, java.util.UUID> {

    Page<LoginHistoryEntry> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    Page<LoginHistoryEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // "Have we ever seen this device/location succeed a login for this user before?" - drives the
    // new-device login alert email (AuthService.maybeSendLoginAlert). Deliberately checks all-time,
    // not a rolling window - a device seen once, even years ago, shouldn't re-trigger every visit.
    boolean existsByUserEmailAndIpAddressAndUserAgentAndEventTypeAndSuccessTrueAndCreatedAtBefore(
            String userEmail, String ipAddress, String userAgent, LoginHistoryEntry.EventType eventType, LocalDateTime before);
}
