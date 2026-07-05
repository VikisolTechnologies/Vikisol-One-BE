package com.vikisol.one.recruitment.repository;

import com.vikisol.one.recruitment.entity.Candidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByEmail(String email);

    Optional<Candidate> findByConvertedEmployeeId(String convertedEmployeeId);

    List<Candidate> findByStatus(Candidate.Status status);

    Page<Candidate> findByJobPostingId(UUID jobPostingId, Pageable pageable);

    List<Candidate> findByAssignedRecruiterIdAndStatus(UUID recruiterId, Candidate.Status status);

    long countByAssignedRecruiterIdAndStatus(UUID recruiterId, Candidate.Status status);
}
