package com.vikisol.one.recruitment.repository;

import com.vikisol.one.recruitment.entity.CandidateFieldChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CandidateFieldChangeRepository extends JpaRepository<CandidateFieldChange, UUID> {
    List<CandidateFieldChange> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
}
