package com.vikisol.one.auth.repository;

import com.vikisol.one.auth.entity.PasswordHistoryEntry;
import com.vikisol.one.auth.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryEntryRepository extends JpaRepository<PasswordHistoryEntry, java.util.UUID> {

    List<PasswordHistoryEntry> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    void deleteByUser(User user);
}
