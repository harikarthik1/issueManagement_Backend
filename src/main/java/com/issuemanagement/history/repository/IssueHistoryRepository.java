package com.issuemanagement.history.repository;

import com.issuemanagement.history.entity.IssueHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueHistoryRepository extends JpaRepository<IssueHistory, Long> {

    @Query("SELECT h FROM IssueHistory h JOIN FETCH h.actor WHERE h.issue.id = :issueId ORDER BY h.createdAt DESC")
    List<IssueHistory> findByIssueIdWithActor(@Param("issueId") Long issueId);
}
