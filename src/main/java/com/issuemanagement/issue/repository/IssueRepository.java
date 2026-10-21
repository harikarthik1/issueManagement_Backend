package com.issuemanagement.issue.repository;

import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    Optional<Issue> findByIssueKey(String issueKey);

    long countByProjectId(Long projectId);

    java.util.List<Issue> findByProjectIdAndArchivedFalse(Long projectId);

    java.util.List<Issue> findByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    java.util.List<Issue> findByProjectIdAndAssigneeIdAndStatusIn(Long projectId, Long assigneeId, java.util.Collection<IssueStatus> statuses);

    java.util.List<Issue> findByAssigneeIdAndStatusIn(Long assigneeId, java.util.Collection<IssueStatus> statuses);

    @Query("SELECT i FROM Issue i WHERE i.project.id = :projectId " +
            "AND i.archived = false " +
            "AND (:status IS NULL OR i.status = :status) " +
            "AND (:priority IS NULL OR i.priority = :priority) " +
            "AND (:type IS NULL OR i.type = :type) " +
            "AND (:assigneeId IS NULL OR (i.assignee IS NOT NULL AND i.assignee.id = :assigneeId))")
    Page<Issue> findProjectIssues(
            @Param("projectId") Long projectId,
            @Param("status") IssueStatus status,
            @Param("priority") IssuePriority priority,
            @Param("type") IssueType type,
            @Param("assigneeId") Long assigneeId,
            Pageable pageable
    );

    // ==========================================
    // Employee Dashboard Queries
    // ==========================================

    @Query("SELECT i.status, count(i) FROM Issue i " +
           "WHERE i.assignee.id = :assigneeId " +
           "AND i.archived = false " +
           "AND i.project.status = com.issuemanagement.project.entity.ProjectStatus.ACTIVE " +
           "GROUP BY i.status")
    java.util.List<Object[]> countEmployeeAssignedIssuesByStatus(@Param("assigneeId") Long assigneeId);

    @Query("SELECT i.priority, count(i) FROM Issue i " +
           "WHERE i.assignee.id = :assigneeId " +
           "AND i.archived = false " +
           "AND i.project.status = com.issuemanagement.project.entity.ProjectStatus.ACTIVE " +
           "AND i.status IN (com.issuemanagement.issue.entity.IssueStatus.OPEN, com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS) " +
           "GROUP BY i.priority")
    java.util.List<Object[]> countEmployeeActiveAssignedIssuesByPriority(@Param("assigneeId") Long assigneeId);

    @Query("SELECT i FROM Issue i JOIN FETCH i.project " +
           "WHERE i.assignee.id = :assigneeId " +
           "AND i.archived = false " +
           "AND i.project.status = com.issuemanagement.project.entity.ProjectStatus.ACTIVE " +
           "AND i.status IN (com.issuemanagement.issue.entity.IssueStatus.OPEN, com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS) " +
           "ORDER BY CASE i.priority " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.CRITICAL THEN 1 " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.HIGH THEN 2 " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.MEDIUM THEN 3 " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.LOW THEN 4 " +
           "  ELSE 5 END ASC, i.createdAt DESC, i.id DESC")
    java.util.List<Issue> findEmployeeActiveAssignedIssuesPrioritized(@Param("assigneeId") Long assigneeId);

    // ==========================================
    // Project Manager Dashboard Queries
    // ==========================================

    @Query("SELECT i.status, count(i) FROM Issue i " +
           "WHERE i.project.id = :projectId " +
           "AND i.archived = false " +
           "GROUP BY i.status")
    java.util.List<Object[]> countProjectIssuesByStatus(@Param("projectId") Long projectId);

    @Query("SELECT i.priority, count(i) FROM Issue i " +
           "WHERE i.project.id = :projectId " +
           "AND i.archived = false " +
           "AND i.status IN (com.issuemanagement.issue.entity.IssueStatus.OPEN, com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS) " +
           "GROUP BY i.priority")
    java.util.List<Object[]> countProjectActiveIssuesByPriority(@Param("projectId") Long projectId);

    @Query("SELECT count(i) FROM Issue i " +
           "WHERE i.project.id = :projectId " +
           "AND i.archived = false " +
           "AND i.assignee IS NULL " +
           "AND i.status IN (com.issuemanagement.issue.entity.IssueStatus.OPEN, com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS)")
    long countProjectUnassignedActiveIssues(@Param("projectId") Long projectId);

    @Query("SELECT i FROM Issue i JOIN FETCH i.project " +
           "WHERE i.project.id = :projectId " +
           "AND i.archived = false " +
           "AND i.assignee IS NULL " +
           "AND i.status IN (com.issuemanagement.issue.entity.IssueStatus.OPEN, com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS) " +
           "ORDER BY CASE i.priority " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.CRITICAL THEN 1 " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.HIGH THEN 2 " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.MEDIUM THEN 3 " +
           "  WHEN com.issuemanagement.issue.entity.IssuePriority.LOW THEN 4 " +
           "  ELSE 5 END ASC, i.createdAt DESC, i.id DESC")
    java.util.List<Issue> findProjectUnassignedActiveIssuesPrioritized(@Param("projectId") Long projectId);

    @Query("SELECT i.assignee.id, i.status, count(i) FROM Issue i " +
           "WHERE i.project.id = :projectId " +
           "AND i.archived = false " +
           "AND i.assignee IS NOT NULL " +
           "GROUP BY i.assignee.id, i.status")
    java.util.List<Object[]> countProjectMemberIssuesByStatus(@Param("projectId") Long projectId);

    @Query("SELECT i.assignee.id, i.priority, count(i) FROM Issue i " +
           "WHERE i.project.id = :projectId " +
           "AND i.archived = false " +
           "AND i.assignee IS NOT NULL " +
           "AND i.status IN (com.issuemanagement.issue.entity.IssueStatus.OPEN, com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS) " +
           "GROUP BY i.assignee.id, i.priority")
    java.util.List<Object[]> countProjectMemberActiveIssuesByPriority(@Param("projectId") Long projectId);

    @Query("SELECT i FROM Issue i WHERE i.project.id IN :projectIds " +
           "AND i.archived = false " +
           "AND (:status IS NULL OR i.status = :status) " +
           "AND (:priority IS NULL OR i.priority = :priority) " +
           "AND (:type IS NULL OR i.type = :type) " +
           "ORDER BY i.updatedAt DESC")
    Page<Issue> findAccessibleIssues(
            @Param("projectIds") java.util.Collection<Long> projectIds,
            @Param("status") IssueStatus status,
            @Param("priority") IssuePriority priority,
            @Param("type") IssueType type,
            Pageable pageable
    );

    @Query("SELECT i FROM Issue i WHERE i.archived = false " +
           "AND (:status IS NULL OR i.status = :status) " +
           "AND (:priority IS NULL OR i.priority = :priority) " +
           "AND (:type IS NULL OR i.type = :type) " +
           "ORDER BY i.updatedAt DESC")
    Page<Issue> findAllAccessibleIssues(
            @Param("status") IssueStatus status,
            @Param("priority") IssuePriority priority,
            @Param("type") IssueType type,
            Pageable pageable
    );
}
