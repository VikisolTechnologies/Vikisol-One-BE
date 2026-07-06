package com.vikisol.one.session.repository;

import com.vikisol.one.session.entity.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActiveSessionRepository extends JpaRepository<ActiveSession, UUID> {

    Optional<ActiveSession> findByJti(String jti);

    List<ActiveSession> findByUserEmailAndRevokedFalseOrderByLastActivityAtDesc(String userEmail);

    List<ActiveSession> findAllByOrderByLastActivityAtDesc();

    @Modifying
    @Query("update ActiveSession s set s.revoked = true where s.userEmail = :userEmail and s.revoked = false")
    void revokeAllForUser(@Param("userEmail") String userEmail);
}
