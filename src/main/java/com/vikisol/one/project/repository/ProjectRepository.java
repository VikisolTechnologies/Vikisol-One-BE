package com.vikisol.one.project.repository;

import com.vikisol.one.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByStatus(Project.Status status);

    List<Project> findByProjectManagerId(UUID projectManagerId);

    Page<Project> findByIsActiveTrue(Pageable pageable);

    Optional<Project> findByCode(String code);
}
