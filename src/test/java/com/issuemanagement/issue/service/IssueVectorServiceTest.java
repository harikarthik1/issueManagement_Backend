package com.issuemanagement.issue.service;

import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueVectorServiceTest {

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private IssueVectorService issueVectorService;

    private Issue issue;

    @BeforeEach
    void setUp() {
        Project project = Project.builder().id(10L).name("Test Project").projectKey("CCP").build();
        issue = Issue.builder()
                .id(2L)
                .issueKey("CCP-1")
                .title("Test issue")
                .description("Test description")
                .status(IssueStatus.OPEN)
                .priority(IssuePriority.HIGH)
                .type(IssueType.BUG)
                .project(project)
                .build();
    }

    @Test
    @DisplayName("indexIssue creates document with valid UUID id and indexes into vectorStore")
    void testIndexIssue() {
        issueVectorService.indexIssue(issue);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());

        List<Document> documents = captor.getValue();
        assertEquals(1, documents.size());

        Document doc = documents.get(0);
        assertDoesNotThrow(() -> UUID.fromString(doc.getId()));
        assertEquals(issueVectorService.toDocumentId(2L), doc.getId());
        assertEquals(2L, doc.getMetadata().get("issueId"));
        assertEquals("10", doc.getMetadata().get("projectId"));
        assertEquals("CCP-1", doc.getMetadata().get("issueKey"));
    }

    @Test
    @DisplayName("deleteIndex passes valid UUID string to vectorStore.delete")
    void testDeleteIndex() {
        issueVectorService.deleteIndex(2L);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).delete(captor.capture());

        List<String> ids = captor.getValue();
        assertEquals(1, ids.size());
        assertDoesNotThrow(() -> UUID.fromString(ids.get(0)));
        assertEquals(issueVectorService.toDocumentId(2L), ids.get(0));
    }
}
