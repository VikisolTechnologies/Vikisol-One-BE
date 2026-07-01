package com.vikisol.one.payroll.repository;

import com.vikisol.one.payroll.entity.PayrollConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollConfigRepository extends JpaRepository<PayrollConfig, UUID> {

    Optional<PayrollConfig> findByKey(String key);

    List<PayrollConfig> findByCategory(String category);
}
