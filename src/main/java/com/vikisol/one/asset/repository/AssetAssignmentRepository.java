package com.vikisol.one.asset.repository;

import com.vikisol.one.asset.entity.AssetAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, UUID> {

    List<AssetAssignment> findByEmployeeIdAndIsActiveTrue(UUID employeeId);

    Optional<AssetAssignment> findByAssetIdAndIsActiveTrue(UUID assetId);
}
