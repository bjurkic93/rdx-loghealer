package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.GitHubConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GitHubConnectionRepository extends JpaRepository<GitHubConnection, String> {
    
    Optional<GitHubConnection> findByProjectIdAndIsActiveTrue(String projectId);
    
    Optional<GitHubConnection> findByRepositoryFullNameAndIsActiveTrue(String repositoryFullName);
    
    boolean existsByProjectIdAndIsActiveTrue(String projectId);
}
