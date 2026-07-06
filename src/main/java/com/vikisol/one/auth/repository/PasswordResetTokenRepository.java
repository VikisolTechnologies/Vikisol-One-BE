package com.vikisol.one.auth.repository;

import com.vikisol.one.auth.entity.PasswordResetToken;
import com.vikisol.one.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    // Invalidate any still-outstanding reset tokens for this user - fired whenever a password
    // actually changes (via reset or otherwise), so an old, un-clicked reset link can never be
    // used after the fact ("old tokens become invalid after password change").
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(User user);

    List<PasswordResetToken> findByUserAndUsedFalse(User user);
}
