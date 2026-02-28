package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.ServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceGroupRepository extends JpaRepository<ServiceGroup, UUID> {

    @Query("SELECT sg FROM ServiceGroup sg WHERE sg.tenant.id = :tenantId AND sg.active = true")
    List<ServiceGroup> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT sg FROM ServiceGroup sg LEFT JOIN FETCH sg.projects LEFT JOIN FETCH sg.databases WHERE sg.id = :id")
    Optional<ServiceGroup> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT DISTINCT sg FROM ServiceGroup sg LEFT JOIN FETCH sg.projects WHERE sg.tenant.id = :tenantId AND sg.active = true")
    List<ServiceGroup> findByTenantIdWithProjects(@Param("tenantId") UUID tenantId);

    @Query("SELECT sg FROM ServiceGroup sg JOIN sg.projects p WHERE p.id = :projectId")
    List<ServiceGroup> findByProjectId(@Param("projectId") UUID projectId);
}
