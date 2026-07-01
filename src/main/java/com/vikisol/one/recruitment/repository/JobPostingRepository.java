package com.vikisol.one.recruitment.repository;

import com.vikisol.one.recruitment.entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    List<JobPosting> findByStatus(JobPosting.Status status);

    List<JobPosting> findByDepartmentId(UUID departmentId);

    Page<JobPosting> findByIsActiveTrue(Pageable pageable);
}
