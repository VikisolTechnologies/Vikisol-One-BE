package com.vikisol.one.assessment.repository;

import com.vikisol.one.assessment.entity.Assessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findByArenaSubmissionId(String arenaSubmissionId);

    Page<Assessment> findByStatus(Assessment.Status status, Pageable pageable);

    Page<Assessment> findByCandidateId(UUID candidateId, Pageable pageable);
}
