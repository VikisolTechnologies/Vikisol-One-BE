package com.vikisol.one.session.repository;

import com.vikisol.one.session.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(UUID familyId);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.familyId = :familyId and r.revoked = false")
    void revokeFamily(@Param("familyId") UUID familyId);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.userEmail = :userEmail and r.revoked = false")
    void revokeAllForUser(@Param("userEmail") String userEmail);

    long countByUserEmailAndRevokedFalse(String userEmail);

    List<RefreshToken> findBySessionJtiAndRevokedFalse(String sessionJti);

    // Retention: once a row is past its own natural expiresAt, it can never be redeemed again
    // (whether it was ever revoked or not) - delete it rather than letting the table grow
    // forever. See ScheduledTasks.purgeStaleSessionData.
    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
