package com.issuemanagement.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issuemanagement.audit.repository.AuditLogRepository;
import com.issuemanagement.comment.repository.IssueCommentRepository;
import com.issuemanagement.history.repository.IssueHistoryRepository;
import com.issuemanagement.identity.dto.LoginRequest;
import com.issuemanagement.identity.dto.RegisterAdminRequest;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.repository.UserRepository;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.repository.ProjectMemberRepository;
import com.issuemanagement.project.repository.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IssueCommentRepository commentRepository;

    @Autowired
    private IssueHistoryRepository historyRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void cleanDatabase() {
        commentRepository.deleteAllInBatch();
        historyRepository.deleteAllInBatch();
        issueRepository.deleteAllInBatch();
        projectMemberRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-admin: successfully registers the initial admin when no admin exists")
    void registerAdmin_SuccessWhenNoAdminExists() throws Exception {
        RegisterAdminRequest request = RegisterAdminRequest.builder()
                .email("initial.admin@example.com")
                .password("SecureAdmin@123")
                .firstName("Super")
                .lastName("Admin")
                .build();

        mockMvc.perform(post("/api/v1/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Administrator registered successfully"))
                .andExpect(jsonPath("$.data.email").value("initial.admin@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Super"))
                .andExpect(jsonPath("$.data.lastName").value("Admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(false))
                .andExpect(jsonPath("$.data.token").doesNotExist());

        // Verify that the registered admin can now explicitly log in
        LoginRequest loginRequest = LoginRequest.builder()
                .email("initial.admin@example.com")
                .password("SecureAdmin@123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.user.email").value("initial.admin@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-admin: rejected with 409 Conflict when an admin already exists")
    void registerAdmin_RejectedWhenAdminAlreadyExists() throws Exception {
        // First registration succeeds
        RegisterAdminRequest firstRequest = RegisterAdminRequest.builder()
                .email("first.admin@example.com")
                .password("SecureAdmin@123")
                .firstName("First")
                .lastName("Admin")
                .build();

        mockMvc.perform(post("/api/v1/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Second registration must be rejected because only one admin is allowed
        RegisterAdminRequest secondRequest = RegisterAdminRequest.builder()
                .email("second.admin@example.com")
                .password("SecureAdmin@123")
                .firstName("Second")
                .lastName("Admin")
                .build();

        mockMvc.perform(post("/api/v1/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An administrator account already exists. Only one admin registration is allowed."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register: alias route functions identically")
    void registerAdmin_AliasRouteFunctionsIdentically() throws Exception {
        RegisterAdminRequest request = RegisterAdminRequest.builder()
                .email("alias.admin@example.com")
                .password("SecureAdmin@123")
                .firstName("Alias")
                .lastName("Admin")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alias.admin@example.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-admin: rejected with 400 Bad Request on validation errors")
    void registerAdmin_ValidationErrors() throws Exception {
        RegisterAdminRequest invalidRequest = RegisterAdminRequest.builder()
                .email("invalid-email")
                .password("short")
                .firstName("")
                .lastName("")
                .build();

        mockMvc.perform(post("/api/v1/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isMap())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists())
                .andExpect(jsonPath("$.validationErrors.firstName").exists())
                .andExpect(jsonPath("$.validationErrors.lastName").exists());
    }
}
