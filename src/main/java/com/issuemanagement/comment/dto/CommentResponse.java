package com.issuemanagement.comment.dto;

import com.issuemanagement.comment.entity.IssueComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Long id;
    private Long issueId;
    private Long authorId;
    private String authorName;
    private String authorEmail;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    public static CommentResponse fromEntity(IssueComment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .issueId(comment.getIssue().getId())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getFirstName() + " " + comment.getAuthor().getLastName())
                .authorEmail(comment.getAuthor().getEmail())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
