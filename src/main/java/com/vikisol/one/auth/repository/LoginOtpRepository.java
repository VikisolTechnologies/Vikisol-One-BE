package com.vikisol.one.auth.repository;

import com.vikisol.one.auth.entity.LoginOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoginOtpRepository extends JpaRepository<LoginOtp, UUID> {

    Optional<LoginOtp> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("update LoginOtp o set o.used = true where o.email = :email and o.used = false")
    void invalidateAllForEmail(@Param("email") String email);
}
