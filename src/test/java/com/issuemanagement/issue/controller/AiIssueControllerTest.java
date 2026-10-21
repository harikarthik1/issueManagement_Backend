package com.issuemanagement.issue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issuemanagement.issue.dto.ai.AiIssueDraftResponse;
import com.issuemanagement.issue.dto.ai.SearchSimilarIssuesRequest;
import com.issuemanagement.issue.dto.ai.SimilarIssueResult;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.service.AiIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiIssueControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiIssueService aiIssueService;

    @InjectMocks
    private AiIssueController aiIssueController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiIssueController).build();
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/issues/ai/search-similar returns similar issues")
    void testSearchSimilarIssues() throws Exception {
        SearchSimilarIssuesRequest request = new SearchSimilarIssuesRequest("Login button error");
        List<SimilarIssueResult> mockResults = List.of(
                new SimilarIssueResult(101L, "APP-101", "Login crash", "OPEN", "HIGH", 0.92)
        );

        when(aiIssueService.searchSimilarIssues(eq(1L), eq("Login button error"))).thenReturn(mockResults);

        mockMvc.perform(post("/api/v1/projects/1/issues/ai/search-similar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].issueKey").value("APP-101"))
                .andExpect(jsonPath("$.data[0].title").value("Login crash"))
                .andExpect(jsonPath("$.data[0].similarityScore").value(0.92));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/issues/ai/draft returns structured draft")
    void testDraftIssue() throws Exception {
        SearchSimilarIssuesRequest request = new SearchSimilarIssuesRequest("Users cannot reset password via SMS");
        AiIssueDraftResponse mockDraft = new AiIssueDraftResponse(
                "Fix SMS password reset failure",
                "**Problem Summary**\nSMS delivery fails",
                IssueType.BUG,
                IssuePriority.HIGH
        );

        when(aiIssueService.draftIssue(eq(1L), eq("Users cannot reset password via SMS"))).thenReturn(mockDraft);

        mockMvc.perform(post("/api/v1/projects/1/issues/ai/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Fix SMS password reset failure"))
                .andExpect(jsonPath("$.data.type").value("BUG"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/issues/ai/sync-vectors returns success message")
    void testSyncProjectVectors() throws Exception {
        when(aiIssueService.syncProjectIssues(1L)).thenReturn(5);

        mockMvc.perform(post("/api/v1/projects/1/issues/ai/sync-vectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully indexed 5 issues into Qdrant"));
    }
}
