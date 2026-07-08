package com.vikisol.one.session.repository;

import com.vikisol.one.session.entity.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // Retention: a revoked session has no further use beyond audit trail - once that trail is old
    // enough nobody needs it, delete it rather than letting the table grow forever (this is what
    // used to happen on every single silent token refresh before rotateJti(), see AuthService).
    @Modifying
    @Query("delete from ActiveSession s where s.revoked = true and s.updatedAt < :cutoff")
    int deleteRevokedBefore(@Param("cutoff") LocalDateTime cutoff);
}
