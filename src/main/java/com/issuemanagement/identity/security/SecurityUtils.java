package com.issuemanagement.identity.security;

import com.issuemanagement.common.exception.UnauthorizedException;
import com.issuemanagement.identity.entity.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal);
        }
        return Optional.empty();
    }

    public static UserPrincipal getRequiredCurrentUserPrincipal() {
        return getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("Authenticated user context is missing or invalid"));
    }

    public static Long getCurrentUserId() {
        return getRequiredCurrentUserPrincipal().getId();
    }

    public static String getCurrentUserEmail() {
        return getRequiredCurrentUserPrincipal().getUsername();
    }

    public static Role getCurrentUserRole() {
        return getRequiredCurrentUserPrincipal().getRole();
    }

    public static boolean isCurrentUserAdmin() {
        return getCurrentUserRole() == Role.ADMIN;
    }

    public static boolean isCurrentUserProjectManager() {
        return getCurrentUserRole() == Role.PROJECT_MANAGER;
    }

    public static String getCurrentSessionId() {
        return getRequiredCurrentUserPrincipal().getSessionId();
    }
}
