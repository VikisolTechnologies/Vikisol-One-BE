package com.vikisol.one.settings.repository;

import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.settings.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRole(RoleEnum role);

    Optional<RolePermission> findByRoleAndModule(RoleEnum role, String module);
}
