package com.vikisol.one.policy.repository;

import com.vikisol.one.policy.entity.CompanyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyPolicyRepository extends JpaRepository<CompanyPolicy, UUID> {
    List<CompanyPolicy> findByActiveTrueOrderByCategoryAscTitleAsc();
    List<CompanyPolicy> findAllByOrderByCategoryAscTitleAsc();
}
