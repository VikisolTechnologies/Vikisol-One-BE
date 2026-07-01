package com.vikisol.one.asset.repository;

import com.vikisol.one.asset.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByStatus(Asset.Status status);

    List<Asset> findByCategory(Asset.Category category);

    Optional<Asset> findByAssetTag(String assetTag);

    Page<Asset> findByStatus(Asset.Status status, Pageable pageable);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(a.assetTag, 5) AS int)), 0) FROM Asset a")
    int findMaxAssetTag();
}
