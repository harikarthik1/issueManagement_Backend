package com.issuemanagement.project.dto;

import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.project.entity.ProjectMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberResponse {
    private Long id;
    private Long projectId;
    private Long userId;
    private String userEmail;
    private String userName;
    private Role userRole;
    private Instant joinedAt;

    public static ProjectMemberResponse fromEntity(ProjectMember member) {
        return ProjectMemberResponse.builder()
                .id(member.getId())
                .projectId(member.getProject().getId())
                .userId(member.getUser().getId())
                .userEmail(member.getUser().getEmail())
                .userName(member.getUser().getFirstName() + " " + member.getUser().getLastName())
                .userRole(member.getUser().getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
