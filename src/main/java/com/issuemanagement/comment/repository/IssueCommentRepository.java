package com.issuemanagement.comment.repository;

import com.issuemanagement.comment.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    @Query("SELECT c FROM IssueComment c JOIN FETCH c.author WHERE c.issue.id = :issueId ORDER BY c.createdAt ASC")
    List<IssueComment> findByIssueIdWithAuthor(@Param("issueId") Long issueId);
}
