package com.issuemanagement.project.repository;

import com.issuemanagement.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectKey(String projectKey);

    boolean existsByProjectKey(String projectKey);

    Page<Project> findByManagerId(Long managerId, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Project p JOIN ProjectMember pm ON pm.project.id = p.id WHERE pm.user.id = :userId")
    Page<Project> findProjectsForMember(@Param("userId") Long userId, Pageable pageable);
}
