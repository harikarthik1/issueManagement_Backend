package com.issuemanagement.assistant.tools;

import com.issuemanagement.comment.entity.IssueComment;
import com.issuemanagement.comment.repository.IssueCommentRepository;
import com.issuemanagement.history.entity.IssueHistory;
import com.issuemanagement.history.repository.IssueHistoryRepository;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.security.SecurityUtils;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.repository.ProjectMemberRepository;
import com.issuemanagement.project.repository.ProjectRepository;
import com.issuemanagement.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class IssueAssistantTools {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAuthorizationService projectAuthService;
    private final IssueCommentRepository commentRepository;
    private final IssueHistoryRepository historyRepository;
    private final VectorStore vectorStore;

    public IssueAssistantTools(
            IssueRepository issueRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectAuthorizationService projectAuthService,
            IssueCommentRepository commentRepository,
            IssueHistoryRepository historyRepository,
            VectorStore vectorStore) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAuthService = projectAuthService;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.vectorStore = vectorStore;
    }

    @Tool(name = "getAccessibleIssues", description = "Retrieve a list of issues accessible to the authenticated user with optional filtering by status (OPEN, IN_PROGRESS, RESOLVED, CLOSED), priority (LOW, MEDIUM, HIGH, CRITICAL), or type (BUG, FEATURE, TASK)")
    @Transactional(readOnly = true)
    public String getAccessibleIssues(
            @ToolParam(description = "Optional status filter: OPEN, IN_PROGRESS, RESOLVED, CLOSED", required = false) String status,
            @ToolParam(description = "Optional priority filter: LOW, MEDIUM, HIGH, CRITICAL", required = false) String priority,
            @ToolParam(description = "Optional type filter: BUG, FEATURE, TASK", required = false) String type,
            @ToolParam(description = "Max number of issues to return (default 10, max 25)", required = false) Integer limit) {

        Long userId = SecurityUtils.getCurrentUserId();
        Role role = SecurityUtils.getCurrentUserRole();

        IssueStatus issueStatus = parseEnum(IssueStatus.class, status);
        IssuePriority issuePriority = parseEnum(IssuePriority.class, priority);
        IssueType issueType = parseEnum(IssueType.class, type);
        int pageSize = (limit != null && limit > 0) ? Math.min(limit, 25) : 10;
        Pageable pageable = PageRequest.of(0, pageSize);

        Page<Issue> page;
        if (role == Role.ADMIN) {
            page = issueRepository.findAllAccessibleIssues(issueStatus, issuePriority, issueType, pageable);
        } else {
            List<Long> accessibleProjectIds = getAccessibleProjectIds(userId, role);
            if (accessibleProjectIds.isEmpty()) {
                return "No accessible projects found for your user account.";
            }
            page = issueRepository.findAccessibleIssues(accessibleProjectIds, issueStatus, issuePriority, issueType, pageable);
        }

        if (page.isEmpty()) {
            return "No issues found matching the specified criteria within your accessible projects.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d issues (showing first %d):\n", page.getTotalElements(), page.getNumberOfElements()));
        for (Issue issue : page.getContent()) {
            sb.append(String.format("- [%s] %s | Status: %s | Priority: %s | Type: %s | Project: %s (%s)\n",
                    issue.getIssueKey(),
                    issue.getTitle(),
                    issue.getStatus(),
                    issue.getPriority(),
                    issue.getType(),
                    issue.getProject().getName(),
                    issue.getProject().getProjectKey()));
        }
        return sb.toString();
    }

    @Tool(name = "getIssueDetails", description = "Retrieve detailed information for a specific issue using its issue key (e.g. 'CORE-1', 'PROJ-101') or numeric ID")
    @Transactional(readOnly = true)
    public String getIssueDetails(
            @ToolParam(description = "The issue key (e.g. 'PROJ-101') or numeric ID", required = true) String issueIdOrKey) {

        Issue issue = resolveIssue(issueIdOrKey);
        if (issue == null) {
            return "Issue '" + issueIdOrKey + "' was not found.";
        }

        if (!canAccess(issue.getProject())) {
            return "Access denied: You do not have permission to view issue " + issue.getIssueKey() + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Issue Details for ").append(issue.getIssueKey()).append(":\n");
        sb.append("- Key: ").append(issue.getIssueKey()).append("\n");
        sb.append("- Title: ").append(issue.getTitle()).append("\n");
        sb.append("- Status: ").append(issue.getStatus()).append("\n");
        sb.append("- Priority: ").append(issue.getPriority()).append("\n");
        sb.append("- Type: ").append(issue.getType()).append("\n");
        sb.append("- Project: ").append(issue.getProject().getName()).append(" (").append(issue.getProject().getProjectKey()).append(")\n");
        sb.append("- Reporter: ").append(issue.getReporter() != null ? issue.getReporter().getEmail() : "Unknown").append("\n");
        sb.append("- Assignee: ").append(issue.getAssignee() != null ? issue.getAssignee().getEmail() : "Unassigned").append("\n");
        sb.append("- Created At: ").append(issue.getCreatedAt()).append("\n");
        sb.append("- Updated At: ").append(issue.getUpdatedAt()).append("\n");
        sb.append("- Description:\n").append(issue.getDescription() != null ? truncate(issue.getDescription(), 1500) : "(No description provided)");

        return sb.toString();
    }

    @Tool(name = "getIssueComments", description = "Retrieve the chronological list of discussion comments for an issue")
    @Transactional(readOnly = true)
    public String getIssueComments(
            @ToolParam(description = "The issue key (e.g. 'PROJ-101') or numeric ID", required = true) String issueIdOrKey) {

        Issue issue = resolveIssue(issueIdOrKey);
        if (issue == null) {
            return "Issue '" + issueIdOrKey + "' was not found.";
        }

        if (!canAccess(issue.getProject())) {
            return "Access denied: You do not have permission to view comments for issue " + issue.getIssueKey() + ".";
        }

        List<IssueComment> comments = commentRepository.findByIssueIdWithAuthor(issue.getId());
        if (comments == null || comments.isEmpty()) {
            return "No comments recorded for issue " + issue.getIssueKey() + ".";
        }

        // Chronological ordering (oldest to newest)
        List<IssueComment> sortedComments = new ArrayList<>(comments);
        sortedComments.sort(Comparator.comparing(IssueComment::getCreatedAt));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Comments for %s (%d recorded):\n", issue.getIssueKey(), sortedComments.size()));
        for (int i = 0; i < sortedComments.size(); i++) {
            IssueComment c = sortedComments.get(i);
            String author = c.getAuthor() != null ? c.getAuthor().getFirstName() + " " + c.getAuthor().getLastName() + " (" + c.getAuthor().getEmail() + ")" : "Unknown";
            sb.append(String.format("%d. [%s] by %s:\n   %s\n", i + 1, c.getCreatedAt(), author, truncate(c.getContent(), 600)));
        }
        return sb.toString();
    }

    @Tool(name = "getIssueHistory", description = "Retrieve the authoritative, chronological audit history and lifecycle transitions (status changes, reopens, priority updates, assignments) for an issue")
    @Transactional(readOnly = true)
    public String getIssueHistory(
            @ToolParam(description = "The issue key (e.g. 'PROJ-101') or numeric ID", required = true) String issueIdOrKey) {

        Issue issue = resolveIssue(issueIdOrKey);
        if (issue == null) {
            return "Issue '" + issueIdOrKey + "' was not found.";
        }

        if (!canAccess(issue.getProject())) {
            return "Access denied: You do not have permission to view history for issue " + issue.getIssueKey() + ".";
        }

        List<IssueHistory> histories = historyRepository.findByIssueIdWithActor(issue.getId());
        if (histories == null || histories.isEmpty()) {
            return "No change history recorded for issue " + issue.getIssueKey() + ".";
        }

        // Chronological ordering (oldest to newest)
        List<IssueHistory> chronological = new ArrayList<>(histories);
        chronological.sort(Comparator.comparing(IssueHistory::getCreatedAt));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Lifecycle change history for %s (%d events):\n", issue.getIssueKey(), chronological.size()));
        for (int i = 0; i < chronological.size(); i++) {
            IssueHistory h = chronological.get(i);
            String actorName = h.getActor() != null ? h.getActor().getEmail() : "System";
            sb.append(String.format("%d. [%s] %s by %s | Field: %s | Old: '%s' -> New: '%s'\n",
                    i + 1,
                    h.getCreatedAt(),
                    h.getChangeType(),
                    actorName,
                    h.getFieldName(),
                    h.getOldValue() != null ? h.getOldValue() : "None",
                    h.getNewValue() != null ? h.getNewValue() : "None"));
        }
        return sb.toString();
    }

    @Tool(name = "getProjectIssueSummary", description = "Retrieve aggregated status and priority summary metrics for a project")
    @Transactional(readOnly = true)
    public String getProjectIssueSummary(
            @ToolParam(description = "The project key (e.g. 'CORE') or numeric project ID", required = true) String projectIdOrKey) {

        Project project = resolveProject(projectIdOrKey);
        if (project == null) {
            return "Project '" + projectIdOrKey + "' was not found.";
        }

        if (!canAccess(project)) {
            return "Access denied: You do not have permission to view project " + project.getProjectKey() + ".";
        }

        List<Issue> issues = issueRepository.findByProjectIdAndArchivedFalse(project.getId());
        long total = issues.size();
        long open = issues.stream().filter(i -> i.getStatus() == IssueStatus.OPEN).count();
        long inProgress = issues.stream().filter(i -> i.getStatus() == IssueStatus.IN_PROGRESS).count();
        long resolved = issues.stream().filter(i -> i.getStatus() == IssueStatus.RESOLVED).count();
        long closed = issues.stream().filter(i -> i.getStatus() == IssueStatus.CLOSED).count();
        long critical = issues.stream().filter(i -> i.getPriority() == IssuePriority.CRITICAL).count();
        long high = issues.stream().filter(i -> i.getPriority() == IssuePriority.HIGH).count();

        return String.format("Issue Summary for Project %s (%s):\n" +
                "- Total Active Issues: %d\n" +
                "- Status Breakdown: OPEN=%d, IN_PROGRESS=%d, RESOLVED=%d, CLOSED=%d\n" +
                "- Urgent Priorities: CRITICAL=%d, HIGH=%d\n" +
                "- Project Manager: %s",
                project.getName(), project.getProjectKey(), total, open, inProgress, resolved, closed,
                critical, high,
                project.getManager() != null ? project.getManager().getEmail() : "None");
    }

    @Tool(name = "searchSimilarIssues", description = "Search for semantically similar issues using vector similarity search over existing issue title/description embeddings in Qdrant")
    @Transactional(readOnly = true)
    public String searchSimilarIssues(
            @ToolParam(description = "Natural language problem description or search query", required = true) String query,
            @ToolParam(description = "Optional project key or ID to limit search to a specific project", required = false) String projectIdOrKey) {

        if (query == null || query.isBlank()) {
            return "Please provide a search description or question.";
        }

        Long targetProjectId = null;
        if (projectIdOrKey != null && !projectIdOrKey.isBlank()) {
            Project project = resolveProject(projectIdOrKey);
            if (project == null) {
                return "Project '" + projectIdOrKey + "' was not found.";
            }
            if (!canAccess(project)) {
                return "Access denied: You do not have permission to access project " + project.getProjectKey() + ".";
            }
            targetProjectId = project.getId();
        }

        try {
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(10)
                    .similarityThreshold(0.40);

            if (targetProjectId != null) {
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                searchBuilder.filterExpression(filterBuilder.eq("projectId", String.valueOf(targetProjectId)).build());
            }

            List<Document> documents = vectorStore.similaritySearch(searchBuilder.build());
            if (documents == null || documents.isEmpty()) {
                return "No semantically similar issues found in Qdrant for query: " + query;
            }

            List<String> results = new ArrayList<>();
            for (Document doc : documents) {
                Long issueId = extractIssueId(doc);
                if (issueId == null) {
                    continue;
                }

                Optional<Issue> issueOpt = issueRepository.findById(issueId);
                if (issueOpt.isEmpty() || issueOpt.get().isArchived()) {
                    continue;
                }
                Issue issue = issueOpt.get();

                // Strict authorization check for each candidate!
                if (!canAccess(issue.getProject())) {
                    continue; // Skip unauthorized issues silently
                }

                double score = doc.getScore() != null ? doc.getScore() : 0.0;
                results.add(String.format("- [%s] %s | Status: %s | Priority: %s | Project: %s | Similarity: %.2f\n  Excerpt: %s",
                        issue.getIssueKey(),
                        issue.getTitle(),
                        issue.getStatus(),
                        issue.getPriority(),
                        issue.getProject().getProjectKey(),
                        score,
                        truncate(issue.getDescription(), 200)));
            }

            if (results.isEmpty()) {
                return "No authorized similar issues found matching the query.";
            }

            return String.format("Found %d authorized similar issue(s):\n%s", results.size(), String.join("\n", results));
        } catch (Exception e) {
            log.error("Failed to query Qdrant vector store: {}", e.getMessage(), e);
            return "Semantic similarity search is currently unavailable: " + e.getMessage();
        }
    }

    @Tool(name = "getSimilarIssueResolution", description = "Find how similar past issues or recurring problems were resolved using Qdrant vector search and PostgreSQL resolution history/comments")
    @Transactional(readOnly = true)
    public String getSimilarIssueResolution(
            @ToolParam(description = "Natural language problem description or error message", required = true) String query,
            @ToolParam(description = "Optional project key or ID to restrict search", required = false) String projectIdOrKey) {

        if (query == null || query.isBlank()) {
            return "Please provide a problem description or error message.";
        }

        Long targetProjectId = null;
        if (projectIdOrKey != null && !projectIdOrKey.isBlank()) {
            Project project = resolveProject(projectIdOrKey);
            if (project == null) {
                return "Project '" + projectIdOrKey + "' was not found.";
            }
            if (!canAccess(project)) {
                return "Access denied: You do not have permission to access project " + project.getProjectKey() + ".";
            }
            targetProjectId = project.getId();
        }

        try {
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(10)
                    .similarityThreshold(0.40);

            if (targetProjectId != null) {
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                searchBuilder.filterExpression(filterBuilder.eq("projectId", String.valueOf(targetProjectId)).build());
            }

            List<Document> documents = vectorStore.similaritySearch(searchBuilder.build());
            if (documents == null || documents.isEmpty()) {
                return "No similar past issues found for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            int matched = 0;

            for (Document doc : documents) {
                Long issueId = extractIssueId(doc);
                if (issueId == null) {
                    continue;
                }

                Optional<Issue> issueOpt = issueRepository.findById(issueId);
                if (issueOpt.isEmpty() || issueOpt.get().isArchived()) {
                    continue;
                }
                Issue issue = issueOpt.get();

                if (!canAccess(issue.getProject())) {
                    continue;
                }

                // Filter for resolved or closed issues
                if (issue.getStatus() != IssueStatus.RESOLVED && issue.getStatus() != IssueStatus.CLOSED) {
                    continue;
                }

                matched++;
                double score = doc.getScore() != null ? doc.getScore() : 0.0;
                sb.append(String.format("\n--- Candidate %d: [%s] %s (Status: %s, Similarity: %.2f) ---\n",
                        matched, issue.getIssueKey(), issue.getTitle(), issue.getStatus(), score));

                // Fetch resolution history
                List<IssueHistory> histories = historyRepository.findByIssueIdWithActor(issue.getId());
                Optional<IssueHistory> resolutionEvent = histories.stream()
                        .filter(h -> "status".equalsIgnoreCase(h.getFieldName()) &&
                                ("RESOLVED".equalsIgnoreCase(h.getNewValue()) || "CLOSED".equalsIgnoreCase(h.getNewValue())))
                        .findFirst();

                if (resolutionEvent.isPresent()) {
                    IssueHistory rh = resolutionEvent.get();
                    String resolver = rh.getActor() != null ? rh.getActor().getEmail() : "Unknown";
                    sb.append(String.format("Status Transition: Moved to %s by %s at %s\n", rh.getNewValue(), resolver, rh.getCreatedAt()));
                }

                // Fetch last comments which often explain the fix
                List<IssueComment> comments = commentRepository.findByIssueIdWithAuthor(issue.getId());
                if (!comments.isEmpty()) {
                    sb.append("Recent Discussion / Resolution Notes:\n");
                    int start = Math.max(0, comments.size() - 2);
                    for (int i = start; i < comments.size(); i++) {
                        IssueComment c = comments.get(i);
                        String author = c.getAuthor() != null ? c.getAuthor().getEmail() : "Author";
                        sb.append(String.format("  - [%s]: %s\n", author, truncate(c.getContent(), 300)));
                    }
                } else {
                    sb.append("No comments recorded on this issue.\n");
                }

                if (matched >= 3) {
                    break;
                }
            }

            if (matched == 0) {
                return "No closed or resolved issues were found that match the problem description.";
            }

            return "Similar past resolved issues and historical notes:\n" + sb.toString();
        } catch (Exception e) {
            log.error("Failed to query similar issue resolutions: {}", e.getMessage(), e);
            return "Resolution retrieval failed: " + e.getMessage();
        }
    }

    private Issue resolveIssue(String issueIdOrKey) {
        if (issueIdOrKey == null || issueIdOrKey.isBlank()) {
            return null;
        }
        String trimmed = issueIdOrKey.trim();
        try {
            Long id = Long.parseLong(trimmed);
            Optional<Issue> byId = issueRepository.findById(id);
            if (byId.isPresent()) {
                return byId.get();
            }
        } catch (NumberFormatException ignored) {}

        return issueRepository.findByIssueKey(trimmed.toUpperCase()).orElse(null);
    }

    private Project resolveProject(String projectIdOrKey) {
        if (projectIdOrKey == null || projectIdOrKey.isBlank()) {
            return null;
        }
        String trimmed = projectIdOrKey.trim();
        try {
            Long id = Long.parseLong(trimmed);
            Optional<Project> byId = projectRepository.findById(id);
            if (byId.isPresent()) {
                return byId.get();
            }
        } catch (NumberFormatException ignored) {}

        return projectRepository.findByProjectKey(trimmed.toUpperCase()).orElse(null);
    }

    private boolean canAccess(Project project) {
        Long userId = SecurityUtils.getCurrentUserId();
        Role role = SecurityUtils.getCurrentUserRole();
        return projectAuthService.canAccessProject(project, userId, role);
    }

    private List<Long> getAccessibleProjectIds(Long userId, Role role) {
        if (role == Role.ADMIN) {
            return projectRepository.findAll().stream().map(Project::getId).toList();
        }
        if (role == Role.PROJECT_MANAGER) {
            return projectRepository.findByManagerId(userId, Pageable.unpaged())
                    .getContent()
                    .stream()
                    .map(Project::getId)
                    .toList();
        }
        if (role == Role.EMPLOYEE) {
            return projectRepository.findProjectsForMember(userId, Pageable.unpaged())
                    .getContent()
                    .stream()
                    .map(Project::getId)
                    .toList();
        }
        return Collections.emptyList();
    }

    private Long extractIssueId(Document doc) {
        Object idObj = doc.getMetadata().get("issueId");
        if (idObj instanceof Number num) {
            return num.longValue();
        } else if (idObj != null) {
            try {
                return Long.parseLong(idObj.toString());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String clean = text.trim();
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, maxLength) + "... [truncated]";
    }
}
