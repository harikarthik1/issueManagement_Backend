package com.issuemanagement.assistant.tools;

import com.issuemanagement.comment.entity.IssueComment;
import com.issuemanagement.comment.repository.IssueCommentRepository;
import com.issuemanagement.history.entity.ChangeType;
import com.issuemanagement.history.entity.IssueHistory;
import com.issuemanagement.history.repository.IssueHistoryRepository;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.UserPrincipal;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.repository.ProjectMemberRepository;
import com.issuemanagement.project.repository.ProjectRepository;
import com.issuemanagement.project.service.ProjectAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueAssistantToolsTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectAuthorizationService projectAuthService;

    @Mock
    private IssueCommentRepository commentRepository;

    @Mock
    private IssueHistoryRepository historyRepository;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private IssueAssistantTools tools;

    private User currentUser;
    private Project testProject;
    private Issue testIssue;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(10L)
                .email("employee@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        UserPrincipal principal = UserPrincipal.create(currentUser, "session-test");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        testProject = Project.builder()
                .id(100L)
                .projectKey("ALPHA")
                .name("Alpha Project")
                .manager(currentUser)
                .status(com.issuemanagement.project.entity.ProjectStatus.ACTIVE)
                .build();

        testIssue = Issue.builder()
                .id(500L)
                .issueKey("ALPHA-1")
                .title("Database connection timeout")
                .description("Connection drops under heavy load")
                .status(IssueStatus.OPEN)
                .priority(IssuePriority.HIGH)
                .type(IssueType.BUG)
                .project(testProject)
                .reporter(currentUser)
                .assignee(currentUser)
                .archived(false)
                .createdAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetAccessibleIssues_ReturnsIssues() {
        when(projectRepository.findProjectsForMember(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testProject)));
        when(issueRepository.findAccessibleIssues(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testIssue)));

        String result = tools.getAccessibleIssues("OPEN", "HIGH", "BUG", 10);

        assertThat(result).contains("Found 1 issues");
        assertThat(result).contains("[ALPHA-1] Database connection timeout");
    }

    @Test
    void testGetIssueDetails_Authorized() {
        when(issueRepository.findByIssueKey("ALPHA-1")).thenReturn(Optional.of(testIssue));
        when(projectAuthService.canAccessProject(eq(testProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(true);

        String result = tools.getIssueDetails("ALPHA-1");

        assertThat(result).contains("Issue Details for ALPHA-1");
        assertThat(result).contains("Status: OPEN");
        assertThat(result).contains("Priority: HIGH");
        assertThat(result).contains("Reporter: employee@test.com");
    }

    @Test
    void testGetIssueDetails_Unauthorized() {
        when(issueRepository.findByIssueKey("ALPHA-1")).thenReturn(Optional.of(testIssue));
        when(projectAuthService.canAccessProject(eq(testProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(false);

        String result = tools.getIssueDetails("ALPHA-1");

        assertThat(result).contains("Access denied");
    }

    @Test
    void testGetIssueDetails_NotFound() {
        when(issueRepository.findByIssueKey("NON-EXISTENT")).thenReturn(Optional.empty());

        String result = tools.getIssueDetails("NON-EXISTENT");

        assertThat(result).contains("was not found");
    }

    @Test
    void testGetIssueComments_ChronologicalOrder() {
        when(issueRepository.findByIssueKey("ALPHA-1")).thenReturn(Optional.of(testIssue));
        when(projectAuthService.canAccessProject(eq(testProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(true);

        IssueComment c1 = IssueComment.builder()
                .id(1L)
                .author(currentUser)
                .content("First comment")
                .createdAt(Instant.now().minusSeconds(100))
                .build();
        IssueComment c2 = IssueComment.builder()
                .id(2L)
                .author(currentUser)
                .content("Second comment")
                .createdAt(Instant.now().minusSeconds(50))
                .build();

        when(commentRepository.findByIssueIdWithAuthor(500L)).thenReturn(List.of(c2, c1)); // unsorted

        String result = tools.getIssueComments("ALPHA-1");

        assertThat(result).contains("Comments for ALPHA-1 (2 recorded)");
        int pos1 = result.indexOf("First comment");
        int pos2 = result.indexOf("Second comment");
        assertThat(pos1).isLessThan(pos2);
    }

    @Test
    void testGetIssueHistory_ChronologicalAndHonestWhenEmpty() {
        when(issueRepository.findByIssueKey("ALPHA-1")).thenReturn(Optional.of(testIssue));
        when(projectAuthService.canAccessProject(eq(testProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(true);

        // When empty
        when(historyRepository.findByIssueIdWithActor(500L)).thenReturn(Collections.emptyList());
        String emptyResult = tools.getIssueHistory("ALPHA-1");
        assertThat(emptyResult).contains("No change history recorded for issue ALPHA-1");

        // When populated
        IssueHistory h1 = IssueHistory.builder()
                .id(1L)
                .actor(currentUser)
                .changeType(ChangeType.CREATED)
                .fieldName("status")
                .newValue("OPEN")
                .createdAt(Instant.now().minusSeconds(500))
                .build();
        IssueHistory h2 = IssueHistory.builder()
                .id(2L)
                .actor(currentUser)
                .changeType(ChangeType.STATUS_CHANGED)
                .fieldName("status")
                .oldValue("OPEN")
                .newValue("IN_PROGRESS")
                .createdAt(Instant.now().minusSeconds(200))
                .build();
        when(historyRepository.findByIssueIdWithActor(500L)).thenReturn(List.of(h2, h1)); // unsorted

        String historyResult = tools.getIssueHistory("ALPHA-1");
        assertThat(historyResult).contains("Lifecycle change history for ALPHA-1 (2 events)");
        int h1Pos = historyResult.indexOf("CREATED");
        int h2Pos = historyResult.indexOf("STATUS_CHANGED");
        assertThat(h1Pos).isLessThan(h2Pos);
    }

    @Test
    void testGetProjectIssueSummary_ValidProject() {
        when(projectRepository.findByProjectKey("ALPHA")).thenReturn(Optional.of(testProject));
        when(projectAuthService.canAccessProject(eq(testProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(true);
        when(issueRepository.findByProjectIdAndArchivedFalse(100L)).thenReturn(List.of(testIssue));

        String result = tools.getProjectIssueSummary("ALPHA");

        assertThat(result).contains("Issue Summary for Project Alpha Project (ALPHA)");
        assertThat(result).contains("Total Active Issues: 1");
        assertThat(result).contains("OPEN=1");
    }

    @Test
    void testSearchSimilarIssues_AuthorizationFiltering() {
        Document docAuthorized = new Document("doc-1", "Timeout error", Map.of("issueId", 500L));
        Document docUnauthorized = new Document("doc-2", "Secret error", Map.of("issueId", 600L));

        Project secretProject = Project.builder().id(200L).projectKey("SECRET").name("Secret").build();
        Issue secretIssue = Issue.builder().id(600L).issueKey("SECRET-1").project(secretProject).build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docAuthorized, docUnauthorized));
        when(issueRepository.findById(500L)).thenReturn(Optional.of(testIssue));
        when(issueRepository.findById(600L)).thenReturn(Optional.of(secretIssue));

        when(projectAuthService.canAccessProject(eq(testProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(true);
        when(projectAuthService.canAccessProject(eq(secretProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(false);

        String result = tools.searchSimilarIssues("connection timeout", null);

        assertThat(result).contains("ALPHA-1");
        assertThat(result).doesNotContain("SECRET-1");
    }

    @Test
    void testGetSimilarIssueResolution_FindsResolvedAndIncludesCommentsAndHistory() {
        testIssue.setStatus(IssueStatus.RESOLVED);
        Document docResolved = new Document("doc-1", "Timeout resolution", Map.of("issueId", 500L));

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docResolved));
        when(issueRepository.findById(500L)).thenReturn(Optional.of(testIssue));
        when(projectAuthService.canAccessProject(eq(testProject), eq(10L), eq(Role.EMPLOYEE))).thenReturn(true);

        IssueHistory resHistory = IssueHistory.builder()
                .actor(currentUser)
                .fieldName("status")
                .oldValue("IN_PROGRESS")
                .newValue("RESOLVED")
                .createdAt(Instant.now().minusSeconds(60))
                .build();
        when(historyRepository.findByIssueIdWithActor(500L)).thenReturn(List.of(resHistory));

        IssueComment fixComment = IssueComment.builder()
                .author(currentUser)
                .content("Increased connection pool size to 50")
                .createdAt(Instant.now().minusSeconds(120))
                .build();
        when(commentRepository.findByIssueIdWithAuthor(500L)).thenReturn(List.of(fixComment));

        String result = tools.getSimilarIssueResolution("connection timeout", null);

        assertThat(result).contains("Candidate 1: [ALPHA-1]");
        assertThat(result).contains("Status Transition: Moved to RESOLVED");
        assertThat(result).contains("Increased connection pool size to 50");
    }
}
