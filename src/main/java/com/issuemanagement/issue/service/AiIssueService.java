package com.issuemanagement.issue.service;

import com.issuemanagement.issue.dto.ai.AiIssueDraftResponse;
import com.issuemanagement.issue.dto.ai.SimilarIssueResult;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.service.ProjectAuthorizationService;
import com.issuemanagement.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiIssueService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ProjectService projectService;
    private final ProjectAuthorizationService projectAuthService;
    private final IssueRepository issueRepository;
    private final IssueVectorService issueVectorService;

    @Transactional(readOnly = true)
    public List<SimilarIssueResult> searchSimilarIssues(Long projectId, String description) {
        Project project = projectService.getActiveProjectEntity(projectId);
        projectAuthService.checkProjectAccess(project);

        try {
            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(description)
                    .topK(5)
                    .similarityThreshold(0.50)
                    .filterExpression(filterBuilder.eq("projectId", String.valueOf(projectId)).build())
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            if (documents == null || documents.isEmpty()) {
                return Collections.emptyList();
            }

            return documents.stream()
                    .filter(doc -> {
                        Object docProjId = doc.getMetadata().get("projectId");
                        if (docProjId == null) {
                            return true;
                        }
                        return Objects.equals(String.valueOf(docProjId), String.valueOf(projectId));
                    })
                    .map(this::mapDocumentToResult)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to query vector store for similar issues in project {}: {}", projectId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Transactional(readOnly = true)
    public AiIssueDraftResponse draftIssue(Long projectId, String description) {
        Project project = projectService.getActiveProjectEntity(projectId);
        projectAuthService.checkProjectAccess(project);

        String systemPrompt = """
                You are an expert technical QA engineer and software issue manager.
                Analyze the user's natural language problem report and produce a structured issue draft.
                
                Guidelines:
                1. Title: Concise, clear summary of the issue (under 100 characters).
                2. Description: Formatted in clear Markdown with the following sections:
                   - **Problem Summary**
                   - **Steps to Reproduce** (infer logical steps if not fully detailed)
                   - **Expected Behavior**
                   - **Actual Behavior**
                3. type: Strictly one of: BUG, TASK, FEATURE.
                   - Use BUG for defects, unexpected errors, crashes, or functional bugs.
                   - Use FEATURE for new feature requests, enhancements, or additions.
                   - Use TASK for chore, setup, configuration, or documentation requests.
                4. priority: Strictly one of: LOW, MEDIUM, HIGH, CRITICAL.
                   - Use CRITICAL for system down, security vulnerability, or data loss.
                   - Use HIGH for major workflow blockers.
                   - Use MEDIUM for general bugs and features.
                   - Use LOW for cosmetic, typo, or minor improvements.
                """;

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(description)
                    .call()
                    .entity(AiIssueDraftResponse.class);
        } catch (Exception e) {
            log.error("Error generating AI issue draft: {}", e.getMessage(), e);
            // Fallback response if LLM call fails
            return new AiIssueDraftResponse(
                    generateFallbackTitle(description),
                    "**Problem Summary**\n" + description + "\n\n**Steps to Reproduce**\n1. \n\n**Expected Behavior**\n\n\n**Actual Behavior**\n",
                    IssueType.BUG,
                    IssuePriority.MEDIUM
            );
        }
    }

    @Transactional(readOnly = true)
    public int syncProjectIssues(Long projectId) {
        Project project = projectService.getActiveProjectEntity(projectId);
        projectAuthService.checkProjectAccess(project);

        List<Issue> issues = issueRepository.findByProjectIdAndArchivedFalse(projectId);
        issueVectorService.batchIndexIssues(issues);
        return issues.size();
    }

    private SimilarIssueResult mapDocumentToResult(Document doc) {
        Map<String, Object> meta = doc.getMetadata();

        Long issueId = null;
        Object idObj = meta.get("issueId");
        if (idObj instanceof Number num) {
            issueId = num.longValue();
        } else if (idObj != null) {
            try {
                issueId = Long.parseLong(idObj.toString());
            } catch (NumberFormatException ignored) {}
        }

        String issueKey = (String) meta.getOrDefault("issueKey", "");
        String title = (String) meta.get("title");
        if (title == null || title.isBlank()) {
            title = extractTitleFromContent(doc.getText());
        }

        String status = (String) meta.getOrDefault("status", "OPEN");
        String priority = (String) meta.getOrDefault("priority", "MEDIUM");
        double score = doc.getScore() != null ? doc.getScore() : 0.0;

        return new SimilarIssueResult(issueId, issueKey, title, status, priority, score);
    }

    private String extractTitleFromContent(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith("Title: ")) {
            int newline = text.indexOf('\n');
            return newline > 0 ? text.substring(7, newline).trim() : text.substring(7).trim();
        }
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    private String generateFallbackTitle(String description) {
        if (description == null || description.isBlank()) {
            return "Reported Issue";
        }
        String firstLine = description.lines().findFirst().orElse(description).trim();
        return firstLine.length() > 80 ? firstLine.substring(0, 77) + "..." : firstLine;
    }
}
