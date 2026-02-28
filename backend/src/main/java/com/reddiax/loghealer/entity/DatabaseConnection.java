package com.reddiax.loghealer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "database_connection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_group_id", nullable = false)
    private ServiceGroup serviceGroup;

    @Column(nullable = false)
    private String name;

    @Column(name = "db_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DatabaseType dbType;

    @Column(name = "host")
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "database_name")
    private String databaseName;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum DatabaseType {
        POSTGRESQL, MYSQL, MARIADB, MONGODB, REDIS, ELASTICSEARCH
    }
}
