package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, UUID> {

    List<DatabaseConnection> findByServiceGroupId(UUID serviceGroupId);
}
