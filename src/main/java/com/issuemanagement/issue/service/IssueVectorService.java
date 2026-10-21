package com.issuemanagement.issue.service;

import com.issuemanagement.issue.entity.Issue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueVectorService {

    private final VectorStore vectorStore;

    public void indexIssue(Issue issue) {
        try {
            String content = "Title: " + issue.getTitle() + "\nDescription: " + (issue.getDescription() != null ? issue.getDescription() : "");
            Map<String, Object> metadata = Map.of(
                    "issueId", issue.getId(),
                    "projectId", String.valueOf(issue.getProject().getId()),
                    "issueKey", issue.getIssueKey(),
                    "title", issue.getTitle(),
                    "status", issue.getStatus().name(),
                    "priority", issue.getPriority().name(),
                    "type", issue.getType().name()
            );
            Document document = new Document(toDocumentId(issue.getId()), content, metadata);
            vectorStore.add(List.of(document));
            log.info("Indexed issue {} into Qdrant", issue.getIssueKey());
        } catch (Exception e) {
            log.error("Failed to index issue {} into Qdrant: {}", issue.getIssueKey(), e.getMessage());
        }
    }

    public void batchIndexIssues(List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        try {
            List<Document> documents = issues.stream().map(issue -> {
                String content = "Title: " + issue.getTitle() + "\nDescription: " + (issue.getDescription() != null ? issue.getDescription() : "");
                Map<String, Object> metadata = Map.of(
                        "issueId", issue.getId(),
                        "projectId", String.valueOf(issue.getProject().getId()),
                        "issueKey", issue.getIssueKey(),
                        "title", issue.getTitle(),
                        "status", issue.getStatus().name(),
                        "priority", issue.getPriority().name(),
                        "type", issue.getType().name()
                );
                return new Document(toDocumentId(issue.getId()), content, metadata);
            }).toList();

            vectorStore.add(documents);
            log.info("Successfully batch indexed {} issues into Qdrant", documents.size());
        } catch (Exception e) {
            log.error("Failed to batch index issues into Qdrant: {}", e.getMessage());
        }
    }

    public void deleteIndex(Long issueId) {
        try {
            vectorStore.delete(List.of(toDocumentId(issueId)));
            log.info("Deleted issue {} from Qdrant index", issueId);
        } catch (Exception e) {
            log.error("Failed to delete issue {} from Qdrant: {}", issueId, e.getMessage());
        }
    }

    public String toDocumentId(Long issueId) {
        return UUID.nameUUIDFromBytes(("issue-" + issueId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
