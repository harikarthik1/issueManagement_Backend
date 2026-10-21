package com.issuemanagement.project.service;

import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.security.SecurityUtils;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {

    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public boolean canAccessProject(Project project, Long userId, Role role) {
        if (role == Role.ADMIN) {
            return true;
        }
        if (role == Role.PROJECT_MANAGER) {
            return project.getManager() != null && project.getManager().getId().equals(userId);
        }
        if (role == Role.EMPLOYEE) {
            return projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public void checkProjectAccess(Project project) {
        Long userId = SecurityUtils.getCurrentUserId();
        Role role = SecurityUtils.getCurrentUserRole();
        if (!canAccessProject(project, userId, role)) {
            throw new ForbiddenException("You do not have permission to access project: " + project.getProjectKey());
        }
    }

    @Transactional(readOnly = true)
    public void checkMembershipManagementPermission(Project project) {
        Long userId = SecurityUtils.getCurrentUserId();
        Role role = SecurityUtils.getCurrentUserRole();

        if (role == Role.ADMIN) {
            return;
        }

        if (role == Role.PROJECT_MANAGER) {
            if (project.getManager() == null || !project.getManager().getId().equals(userId)) {
                throw new ForbiddenException("Project managers can only manage membership in their own assigned projects");
            }
            return;
        }

        throw new ForbiddenException("Employees are not authorized to manage project membership");
    }

    @Transactional(readOnly = true)
    public boolean isProjectMember(Long projectId, Long userId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
    }
}
