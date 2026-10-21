package com.issuemanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.issuemanagement.comment.dto.CreateCommentRequest;
import com.issuemanagement.comment.dto.UpdateCommentRequest;
import com.issuemanagement.history.entity.ChangeType;
import com.issuemanagement.identity.dto.CreateUserRequest;
import com.issuemanagement.identity.dto.LoginRequest;
import com.issuemanagement.identity.dto.UserStatusUpdateRequest;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.issue.dto.AssignIssueRequest;
import com.issuemanagement.issue.dto.CreateIssueRequest;
import com.issuemanagement.issue.dto.TransitionStatusRequest;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.project.dto.AddMemberRequest;
import com.issuemanagement.project.dto.CreateProjectRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IssueManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.issuemanagement.identity.repository.UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.issuemanagement.comment.repository.IssueCommentRepository commentRepository;

    @Autowired
    private com.issuemanagement.history.repository.IssueHistoryRepository historyRepository;

    @Autowired
    private com.issuemanagement.issue.repository.IssueRepository issueRepository;

    @Autowired
    private com.issuemanagement.project.repository.ProjectMemberRepository projectMemberRepository;

    @Autowired
    private com.issuemanagement.project.repository.ProjectRepository projectRepository;

    @Autowired
    private com.issuemanagement.audit.repository.AuditLogRepository auditLogRepository;

    private String adminToken;
    private Long adminId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = null;
        commentRepository.deleteAllInBatch();
        historyRepository.deleteAllInBatch();
        issueRepository.deleteAllInBatch();
        projectMemberRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User testAdmin = userRepository.save(User.builder()
                .email("admin@issuemanagement.com")
                .password(passwordEncoder.encode("Admin@123"))
                .firstName("System")
                .lastName("Admin")
                .role(Role.ADMIN)
                .active(true)
                .passwordChangeRequired(true)
                .build());
        adminId = testAdmin.getId();

        LoginRequest loginRequest = LoginRequest.builder()
                .email("admin@issuemanagement.com")
                .password("Admin@123")
                .build();

            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String initialToken = root.path("data").path("token").asText();
            boolean changeRequired = root.path("data").path("user").path("passwordChangeRequired").asBoolean();

            if (changeRequired) {
                // Verify attempting normal business operations is blocked before password change
                mockMvc.perform(get("/api/v1/users")
                                .header("Authorization", "Bearer " + initialToken))
                        .andExpect(status().isForbidden());

                // Perform password change to establish unique secure password
                com.issuemanagement.identity.dto.ChangePasswordRequest changeReq =
                        new com.issuemanagement.identity.dto.ChangePasswordRequest("Admin@123", "NewAdminSecure@2026!");

                MvcResult changeResult = mockMvc.perform(post("/api/v1/auth/change-password")
                                .header("Authorization", "Bearer " + initialToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(changeReq)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.user.passwordChangeRequired").value(false))
                        .andReturn();

                adminToken = objectMapper.readTree(changeResult.getResponse().getContentAsString())
                        .path("data").path("token").asText();
            } else {
                adminToken = initialToken;
            }
    }

    private String obtainToken(String email, String password) throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private Long createUser(String email, String password, Role role, String first, String last) throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .email(email)
                .password(password)
                .firstName(first)
                .lastName(last)
                .role(role)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    @Test
    @DisplayName("End-to-end lifecycle: Users, Projects, Membership, Issues, Assignment, Workflow, Comments, and History")
    void testCompleteSystemFlow() throws Exception {
        // 1. Create PM and Employees
        Long pmId = createUser("pm.alpha@test.com", "Password@123", Role.PROJECT_MANAGER, "Alice", "PM");
        Long otherPmId = createUser("pm.other@test.com", "Password@123", Role.PROJECT_MANAGER, "Bob", "OtherPM");
        Long emp1Id = createUser("emp1@test.com", "Password@123", Role.EMPLOYEE, "Charlie", "Dev");
        Long emp2Id = createUser("emp2@test.com", "Password@123", Role.EMPLOYEE, "Diana", "Dev");

        String pmToken = obtainToken("pm.alpha@test.com", "Password@123");
        String otherPmToken = obtainToken("pm.other@test.com", "Password@123");
        String emp1Token = obtainToken("emp1@test.com", "Password@123");
        String emp2Token = obtainToken("emp2@test.com", "Password@123");

        // 2. Authorization test: Only Admin can create projects
        CreateProjectRequest projectReq = CreateProjectRequest.builder()
                .name("Alpha Project")
                .projectKey("ALPHA")
                .description("Alpha description")
                .managerId(pmId)
                .build();

        // Employee attempts to create project -> 403 Forbidden
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isForbidden());

        // PM attempts to create project -> 403 Forbidden
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isForbidden());

        // Admin creates project ALPHA
        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectKey").value("ALPHA"))
                .andReturn();

        Long projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Admin also creates project BETA with other PM
        CreateProjectRequest betaReq = CreateProjectRequest.builder()
                .name("Beta Project")
                .projectKey("BETA")
                .description("Beta description")
                .managerId(otherPmId)
                .build();

        MvcResult betaResult = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(betaReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long betaProjectId = objectMapper.readTree(betaResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 3. Project Membership Management
        // Employee cannot add member -> 403
        AddMemberRequest addEmp1 = new AddMemberRequest(emp1Id);
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addEmp1)))
                .andExpect(status().isForbidden());

        // other PM cannot add member to ALPHA -> 403
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + otherPmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addEmp1)))
                .andExpect(status().isForbidden());

        // Assigned PM adds emp1 to ALPHA -> 201 Created
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addEmp1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(emp1Id));

        // Adding emp1 again -> 409 Conflict
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addEmp1)))
                .andExpect(status().isConflict());

        // Adding assigned PM as member -> 400 Bad Request
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest(pmId))))
                .andExpect(status().isBadRequest());

        // emp1 can ALSO belong to project BETA simultaneously!
        mockMvc.perform(post("/api/v1/projects/" + betaProjectId + "/members")
                        .header("Authorization", "Bearer " + otherPmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addEmp1)))
                .andExpect(status().isCreated());

        // 4. Issue Creation and Access Scoping
        // emp2 (not a member of ALPHA) attempts to create issue in ALPHA -> 403 Forbidden
        CreateIssueRequest issueReq = CreateIssueRequest.builder()
                .title("Database indexing optimization")
                .description("Improve query performance")
                .type(IssueType.TASK)
                .priority(IssuePriority.HIGH)
                .build();

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + emp2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issueReq)))
                .andExpect(status().isForbidden());

        // emp1 (member of ALPHA) creates issue in ALPHA -> 201 Created
        MvcResult issueResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issueReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.issueKey").value("ALPHA-1"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();

        Long issueId = objectMapper.readTree(issueResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // emp2 (unauthorized) attempts to view issue by ID -> 403 Forbidden
        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + emp2Token))
                .andExpect(status().isForbidden());

        // 5. Issue Assignment
        // PM assigns issue to emp2 (who is NOT in ALPHA) -> 400 Bad Request
        mockMvc.perform(put("/api/v1/issues/" + issueId + "/assignee")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(emp2Id))))
                .andExpect(status().isBadRequest());

        // PM assigns issue to self (project manager) -> 200 OK
        mockMvc.perform(put("/api/v1/issues/" + issueId + "/assignee")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(pmId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(pmId));

        // PM assigns issue to emp1 (eligible member) -> 200 OK
        mockMvc.perform(put("/api/v1/issues/" + issueId + "/assignee")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(emp1Id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(emp1Id));

        // 6. Workflow Status Transitions
        // Transition OPEN -> RESOLVED directly -> 400 Bad Request (invalid)
        mockMvc.perform(patch("/api/v1/issues/" + issueId + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.RESOLVED))))
                .andExpect(status().isBadRequest());

        // Transition OPEN -> IN_PROGRESS -> 200 OK
        mockMvc.perform(patch("/api/v1/issues/" + issueId + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.IN_PROGRESS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // Transition IN_PROGRESS -> RESOLVED -> 200 OK
        mockMvc.perform(patch("/api/v1/issues/" + issueId + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.RESOLVED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        // Transition RESOLVED -> CLOSED -> 200 OK
        mockMvc.perform(patch("/api/v1/issues/" + issueId + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.CLOSED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Employee tries to reopen CLOSED issue -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/issues/" + issueId + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.OPEN))))
                .andExpect(status().isForbidden());

        // PM reopens CLOSED issue -> 200 OK
        mockMvc.perform(patch("/api/v1/issues/" + issueId + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.OPEN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        // 7. Comments
        // emp2 (unauthorized) tries to comment -> 403 Forbidden
        CreateCommentRequest commentReq = new CreateCommentRequest("Working on this fix");
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", "Bearer " + emp2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isForbidden());

        // emp1 (authorized) adds comment -> 201 Created
        MvcResult commentResult = mockMvc.perform(post("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("Working on this fix"))
                .andReturn();

        Long commentId = objectMapper.readTree(commentResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // emp1 updates their comment -> 200 OK
        mockMvc.perform(patch("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Updated comment text"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Updated comment text"));

        // 8. Issue History Audit Verification
        mockMvc.perform(get("/api/v1/issues/" + issueId + "/history")
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data[?(@.changeType == 'CREATED')]").exists())
                .andExpect(jsonPath("$.data[?(@.changeType == 'ASSIGNEE_CHANGED')]").exists())
                .andExpect(jsonPath("$.data[?(@.changeType == 'STATUS_CHANGED')]").exists());

        // Create additional issues in ALPHA for testing status-dependent unassignment:
        // Issue 2: IN_PROGRESS assigned to emp1
        CreateIssueRequest issue2Req = CreateIssueRequest.builder()
                .title("ALPHA Issue 2 - In Progress")
                .description("In progress task")
                .type(IssueType.TASK)
                .priority(IssuePriority.MEDIUM)
                .build();
        MvcResult issue2Res = mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issue2Req)))
                .andExpect(status().isCreated())
                .andReturn();
        Long issue2Id = objectMapper.readTree(issue2Res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(put("/api/v1/issues/" + issue2Id + "/assignee")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(emp1Id))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue2Id + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.IN_PROGRESS))))
                .andExpect(status().isOk());

        // Issue 3: RESOLVED assigned to emp1
        CreateIssueRequest issue3Req = CreateIssueRequest.builder()
                .title("ALPHA Issue 3 - Resolved")
                .description("Resolved bug")
                .type(IssueType.BUG)
                .priority(IssuePriority.LOW)
                .build();
        MvcResult issue3Res = mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issue3Req)))
                .andExpect(status().isCreated())
                .andReturn();
        Long issue3Id = objectMapper.readTree(issue3Res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(put("/api/v1/issues/" + issue3Id + "/assignee")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(emp1Id))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue3Id + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.IN_PROGRESS))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue3Id + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.RESOLVED))))
                .andExpect(status().isOk());

        // Issue 4: CLOSED assigned to emp1
        CreateIssueRequest issue4Req = CreateIssueRequest.builder()
                .title("ALPHA Issue 4 - Closed")
                .description("Closed feature")
                .type(IssueType.FEATURE)
                .priority(IssuePriority.HIGH)
                .build();
        MvcResult issue4Res = mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issue4Req)))
                .andExpect(status().isCreated())
                .andReturn();
        Long issue4Id = objectMapper.readTree(issue4Res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(put("/api/v1/issues/" + issue4Id + "/assignee")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(emp1Id))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue4Id + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.IN_PROGRESS))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue4Id + "/status")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.RESOLVED))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue4Id + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.CLOSED))))
                .andExpect(status().isOk());

        // Create an issue in project BETA assigned to emp1 (OPEN)
        CreateIssueRequest betaIssueReq = CreateIssueRequest.builder()
                .title("BETA Issue - Open")
                .description("Task in project beta")
                .type(IssueType.TASK)
                .priority(IssuePriority.MEDIUM)
                .build();
        MvcResult betaIssueRes = mockMvc.perform(post("/api/v1/projects/" + betaProjectId + "/issues")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(betaIssueReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long betaIssueId = objectMapper.readTree(betaIssueRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(put("/api/v1/issues/" + betaIssueId + "/assignee")
                        .header("Authorization", "Bearer " + otherPmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(emp1Id))))
                .andExpect(status().isOk());

        // Create an unassigned CRITICAL issue in ALPHA
        CreateIssueRequest unassignedReq = CreateIssueRequest.builder()
                .title("ALPHA Unassigned Critical Bug")
                .description("Needs immediate assignment")
                .type(IssueType.BUG)
                .priority(IssuePriority.CRITICAL)
                .build();
        MvcResult unassignedRes = mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unassignedReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long unassignedIssueId = objectMapper.readTree(unassignedRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // ==========================================
        // 8b. Employee & Project Manager Dashboards
        // ==========================================
        // Employee Dashboard for emp1
        mockMvc.perform(get("/api/v1/dashboard/employee")
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(emp1Id))
                .andExpect(jsonPath("$.data.totalAssignedIssues").value(5))
                .andExpect(jsonPath("$.data.openCount").value(2))
                .andExpect(jsonPath("$.data.inProgressCount").value(1))
                .andExpect(jsonPath("$.data.resolvedCount").value(1))
                .andExpect(jsonPath("$.data.closedCount").value(1))
                .andExpect(jsonPath("$.data.highPriorityActiveCount").value(1))
                .andExpect(jsonPath("$.data.criticalPriorityActiveCount").value(0))
                .andExpect(jsonPath("$.data.activeAssignedIssues", hasSize(3)))
                .andExpect(jsonPath("$.data.activeAssignedIssues[?(@.id == " + unassignedIssueId + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.activeAssignedIssues[?(@.id == " + issue3Id + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.activeAssignedIssues[?(@.id == " + issue4Id + ")]").doesNotExist());

        // Employee Dashboard for emp2 (empty state with 0 assigned issues)
        mockMvc.perform(get("/api/v1/dashboard/employee")
                        .header("Authorization", "Bearer " + emp2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAssignedIssues").value(0))
                .andExpect(jsonPath("$.data.openCount").value(0))
                .andExpect(jsonPath("$.data.activeAssignedIssues", hasSize(0)));

        // Project Manager Dashboard for project ALPHA by assigned PM (Alice)
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/dashboard")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.totalIssues").value(5))
                .andExpect(jsonPath("$.data.openCount").value(2))
                .andExpect(jsonPath("$.data.inProgressCount").value(1))
                .andExpect(jsonPath("$.data.resolvedCount").value(1))
                .andExpect(jsonPath("$.data.closedCount").value(1))
                .andExpect(jsonPath("$.data.pendingWorkCount").value(3))
                .andExpect(jsonPath("$.data.criticalPriorityActiveCount").value(1))
                .andExpect(jsonPath("$.data.highPriorityActiveCount").value(1))
                .andExpect(jsonPath("$.data.unassignedActiveCount").value(1))
                .andExpect(jsonPath("$.data.unassignedActiveIssues", hasSize(1)))
                .andExpect(jsonPath("$.data.unassignedActiveIssues[0].id").value(unassignedIssueId))
                .andExpect(jsonPath("$.data.memberWorkloads").isNotEmpty())
                .andExpect(jsonPath("$.data.memberWorkloads[?(@.employeeId == " + emp1Id + ")].totalAssignedCount").value(4));

        // Unrelated PM (Bob) cannot view ALPHA dashboard -> 403 Forbidden
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/dashboard")
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isForbidden());

        // Ordinary employee (emp1) cannot view manager dashboard -> 403 Forbidden
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/dashboard")
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isForbidden());

        // Admin can view any project dashboard -> 200 OK
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalIssues").value(5));

        // Drill-down endpoint test: query project issues by status & assignee
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/issues")
                        .param("status", "OPEN")
                        .param("assigneeId", emp1Id.toString())
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(issueId));

        // ==========================================
        // 8c. Issue Reopening by Active Project Member
        // ==========================================
        // emp1 (active project member) reopens CLOSED issue4
        mockMvc.perform(post("/api/v1/issues/" + issue4Id + "/reopen")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.issuemanagement.issue.dto.ReopenIssueRequest("Customer reproduced regression"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        // Verify history records status transition to OPEN
        mockMvc.perform(get("/api/v1/issues/" + issue4Id + "/history")
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.oldValue == 'CLOSED' && @.newValue == 'OPEN')]").exists());

        // Re-close issue4 with pmToken for subsequent removal checks
        mockMvc.perform(patch("/api/v1/issues/" + issue4Id + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.IN_PROGRESS))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue4Id + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.RESOLVED))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/issues/" + issue4Id + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionStatusRequest(IssueStatus.CLOSED))))
                .andExpect(status().isOk());

        // 9. Membership Removal Authorization & Stale Assignment Cleanup (Scenario A)
        // Unauthorized ordinary employee cannot remove member -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + emp1Id)
                        .header("Authorization", "Bearer " + emp2Token))
                .andExpect(status().isForbidden());

        // Unrelated Project Manager (otherPm) cannot remove member from ALPHA -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + emp1Id)
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isForbidden());

        // Authorized PM removes emp1 from project ALPHA -> 204 No Content
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + emp1Id)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // Removing non-member again -> 404 Not Found
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + emp1Id)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNotFound());

        // User account still exists and is active
        mockMvc.perform(get("/api/v1/users/" + emp1Id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(emp1Id))
                .andExpect(jsonPath("$.data.active").value(true));

        // OPEN issue (issueId): unassigned
        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reporterId").value(emp1Id))
                .andExpect(jsonPath("$.data.assigneeId").isEmpty());

        // IN_PROGRESS issue (issue2Id): unassigned
        mockMvc.perform(get("/api/v1/issues/" + issue2Id)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").isEmpty());

        // RESOLVED issue (issue3Id): assignment RETAINED
        mockMvc.perform(get("/api/v1/issues/" + issue3Id)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(emp1Id));

        // CLOSED issue (issue4Id): assignment RETAINED
        mockMvc.perform(get("/api/v1/issues/" + issue4Id)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(emp1Id));

        // BETA project issue: assignment in other project RETAINED
        mockMvc.perform(get("/api/v1/issues/" + betaIssueId)
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(emp1Id));

        // History check: verify unassignment reason in issueId
        mockMvc.perform(get("/api/v1/issues/" + issueId + "/history")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.newValue == 'Unassigned (Member Removed from Project)')]").exists());

        // Comments still exist as historical records
        mockMvc.perform(get("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].authorId").value(emp1Id));

        // 10. Immediate Project Access Revocation after Removal
        // emp1 cannot view project ALPHA details -> 403 Forbidden
        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isForbidden());

        // emp1 cannot view project ALPHA issue list -> 403 Forbidden
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isForbidden());

        // emp1 cannot create issues in project ALPHA -> 403 Forbidden
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issueReq)))
                .andExpect(status().isForbidden());

        // emp1 cannot add comments to issue in ALPHA -> 403 Forbidden
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Sneaky comment"))))
                .andExpect(status().isForbidden());

        // emp1 cannot edit or delete their old comments in ALPHA -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Hacker update"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isForbidden());

        // 11. Reassigned Project Manager loses comment deletion permission
        // Reassign manager of ALPHA from pm.alpha (Alice) to pm.other (Bob)
        mockMvc.perform(put("/api/v1/projects/" + projectId + "/manager")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.issuemanagement.project.dto.AssignManagerRequest(otherPmId))))
                .andExpect(status().isOk());

        // Alice (former PM) attempts to delete comment in ALPHA -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isForbidden());

        // Bob (new PM) can delete comment in ALPHA -> 204 No Content
        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isNoContent());

        // 12. Account Deactivation Workflows & Guards (Scenario B)
        // Self-deactivation guard: Admin attempts to deactivate own account -> 400 Bad Request
        mockMvc.perform(patch("/api/v1/users/" + adminId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserStatusUpdateRequest(false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Administrators cannot deactivate their own account"));

        // Admin deactivates emp1 (who currently has an active OPEN issue in BETA project)
        mockMvc.perform(patch("/api/v1/users/" + emp1Id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserStatusUpdateRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Immediate JWT Rejection: Existing emp1Token is rejected on the very next request -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/users/" + emp1Id)
                        .header("Authorization", "Bearer " + emp1Token))
                .andExpect(status().isUnauthorized());

        // Inactive user cannot login -> 401 Unauthorized
        LoginRequest inactiveLogin = LoginRequest.builder()
                .email("emp1@test.com")
                .password("Password@123")
                .build();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiveLogin)))
                .andExpect(status().isUnauthorized());

        // Excluded from eligible employees list
        mockMvc.perform(get("/api/v1/users/eligible/employees")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + emp1Id + ")]").doesNotExist());

        // Cannot add inactive employee as member to a project -> 400 Bad Request
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + otherPmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest(emp1Id))))
                .andExpect(status().isBadRequest());

        // Cannot assign inactive employee to an issue -> 400 Bad Request
        mockMvc.perform(put("/api/v1/issues/" + issueId + "/assignee")
                        .header("Authorization", "Bearer " + otherPmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIssueRequest(emp1Id))))
                .andExpect(status().isBadRequest());

        // Global Assignment Cleanup on Deactivation:
        // betaIssue in project BETA was OPEN and assigned to emp1 -> now unassigned!
        mockMvc.perform(get("/api/v1/issues/" + betaIssueId)
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").isEmpty());

        // History entry for betaIssue has "Unassigned (Account Deactivated)"
        mockMvc.perform(get("/api/v1/issues/" + betaIssueId + "/history")
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.newValue == 'Unassigned (Account Deactivated)')]").exists());

        // Historical RESOLVED and CLOSED issues retain their assignments
        mockMvc.perform(get("/api/v1/issues/" + issue3Id)
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(emp1Id));

        mockMvc.perform(get("/api/v1/issues/" + issue4Id)
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(emp1Id));

        // Membership in project BETA is preserved (deactivation does not remove project memberships)
        org.junit.jupiter.api.Assertions.assertTrue(
                projectMemberRepository.existsByProjectIdAndUserId(betaProjectId, emp1Id),
                "Project membership must be preserved on account deactivation"
        );
    }
}
