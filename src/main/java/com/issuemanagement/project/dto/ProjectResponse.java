package com.issuemanagement.project.dto;

import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.entity.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private Long id;
    private String name;
    private String projectKey;
    private String description;
    private ProjectStatus status;
    private Long managerId;
    private String managerName;
    private String managerEmail;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProjectResponse fromEntity(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .projectKey(project.getProjectKey())
                .description(project.getDescription())
                .status(project.getStatus())
                .managerId(project.getManager() != null ? project.getManager().getId() : null)
                .managerName(project.getManager() != null
                        ? project.getManager().getFirstName() + " " + project.getManager().getLastName()
                        : null)
                .managerEmail(project.getManager() != null ? project.getManager().getEmail() : null)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
