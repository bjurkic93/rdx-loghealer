package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByApiKey(String apiKey);

    @Query("SELECT p FROM Project p WHERE p.tenant.id = :tenantId AND p.active = true")
    List<Project> findByTenantId(@Param("tenantId") UUID tenantId);

    boolean existsByApiKey(String apiKey);

    @Query("SELECT p FROM Project p WHERE p.packagePrefix IS NOT NULL AND p.active = true")
    List<Project> findAllWithPackagePrefix();

    @Query("SELECT p FROM Project p WHERE p.packagePrefix = :packagePrefix AND p.active = true")
    Optional<Project> findByPackagePrefix(@Param("packagePrefix") String packagePrefix);

    @Query("SELECT p FROM Project p WHERE p.name = :name AND p.active = true")
    Optional<Project> findByName(@Param("name") String name);

    @Query("SELECT p FROM Project p WHERE p.projectKey = :projectKey AND p.active = true")
    Optional<Project> findByProjectKey(@Param("projectKey") String projectKey);
}
