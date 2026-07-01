package com.vikisol.one.resource.repository;

import com.vikisol.one.resource.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findByCategory(Resource.ResourceCategory category);

    List<Resource> findByIsPublicTrue();

    List<Resource> findByIsActiveTrue();

    List<Resource> findByTitleContainingIgnoreCase(String title);
}
