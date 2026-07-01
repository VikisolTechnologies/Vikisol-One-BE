package com.vikisol.one.recruitment.repository;

import com.vikisol.one.recruitment.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByCandidateId(UUID candidateId);

    List<Interview> findByInterviewerId(UUID interviewerId);

    List<Interview> findByScheduledDate(LocalDate scheduledDate);

    List<Interview> findByInterviewerIdAndScheduledDateGreaterThanEqual(UUID interviewerId, LocalDate date);
}
