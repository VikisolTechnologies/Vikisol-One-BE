package com.vikisol.one.designation.repository;

import com.vikisol.one.designation.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    Optional<Designation> findByTitle(String title);

    List<Designation> findByIsActiveTrue();
}
