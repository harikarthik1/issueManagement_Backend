package com.issuemanagement.issue.service;

import com.issuemanagement.issue.dto.ai.AiIssueDraftResponse;
import com.issuemanagement.issue.dto.ai.SimilarIssueResult;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.entity.ProjectStatus;
import com.issuemanagement.project.service.ProjectAuthorizationService;
import com.issuemanagement.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiIssueServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectAuthorizationService projectAuthService;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueVectorService issueVectorService;

    @InjectMocks
    private AiIssueService aiIssueService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(1L)
                .projectKey("PROJ")
                .name("Project Alpha")
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("searchSimilarIssues returns matching issues within project")
    void testSearchSimilarIssues() {
        when(projectService.getActiveProjectEntity(1L)).thenReturn(project);
        doNothing().when(projectAuthService).checkProjectAccess(project);

        Document doc = Document.builder()
                .id("10")
                .text("Title: Login button fails\nDescription: Click doesn't work")
                .metadata(Map.of(
                        "issueId", 10L,
                        "projectId", 1L,
                        "issueKey", "PROJ-10",
                        "title", "Login button fails",
                        "status", "OPEN",
                        "priority", "HIGH",
                        "type", "BUG"
                ))
                .score(0.88)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<SimilarIssueResult> results = aiIssueService.searchSimilarIssues(1L, "Login is not clicking");

        assertNotNull(results);
        assertEquals(1, results.size());
        SimilarIssueResult result = results.get(0);
        assertEquals(10L, result.issueId());
        assertEquals("PROJ-10", result.issueKey());
        assertEquals("Login button fails", result.title());
        assertEquals("OPEN", result.status());
        assertEquals("HIGH", result.priority());
        assertEquals(0.88, result.similarityScore(), 0.001);
    }

    @Test
    @DisplayName("syncProjectIssues indexes all non-archived issues")
    void testSyncProjectIssues() {
        when(projectService.getActiveProjectEntity(1L)).thenReturn(project);
        doNothing().when(projectAuthService).checkProjectAccess(project);

        Issue issue1 = Issue.builder().id(1L).issueKey("PROJ-1").title("Issue 1").project(project).build();
        Issue issue2 = Issue.builder().id(2L).issueKey("PROJ-2").title("Issue 2").project(project).build();

        when(issueRepository.findByProjectIdAndArchivedFalse(1L)).thenReturn(List.of(issue1, issue2));

        int count = aiIssueService.syncProjectIssues(1L);

        assertEquals(2, count);
        verify(issueVectorService, times(1)).batchIndexIssues(List.of(issue1, issue2));
    }
}
