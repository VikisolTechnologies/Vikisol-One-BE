package com.vikisol.one.leave.repository;

import com.vikisol.one.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByCode(String code);

    List<LeaveType> findByIsActiveTrue();
}
