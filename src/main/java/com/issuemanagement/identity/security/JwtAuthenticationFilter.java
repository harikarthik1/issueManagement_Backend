package com.issuemanagement.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (!userDetails.isEnabled()) {
                    log.warn("User account is disabled: {}", username);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    com.issuemanagement.common.exception.ErrorResponse errorResponse =
                            com.issuemanagement.common.exception.ErrorResponse.builder()
                                    .timestamp(java.time.Instant.now())
                                    .status(org.springframework.http.HttpStatus.UNAUTHORIZED.value())
                                    .error(org.springframework.http.HttpStatus.UNAUTHORIZED.getReasonPhrase())
                                    .message("User account is deactivated")
                                    .path(request.getRequestURI())
                                    .build();
                    new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                            .writeValue(response.getOutputStream(), errorResponse);
                    return;
                }

                if (userDetails instanceof UserPrincipal principal && principal.isPasswordChangeRequired()) {
                    String path = request.getRequestURI();
                    if (!path.startsWith("/api/v1/auth")) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                        com.issuemanagement.common.exception.ErrorResponse errorResponse =
                                com.issuemanagement.common.exception.ErrorResponse.builder()
                                        .timestamp(java.time.Instant.now())
                                        .status(org.springframework.http.HttpStatus.FORBIDDEN.value())
                                        .error(org.springframework.http.HttpStatus.FORBIDDEN.getReasonPhrase())
                                        .message("Password change required: The default initial password must be changed via /api/v1/auth/change-password before accessing system operations")
                                        .path(path)
                                        .build();
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                                .writeValue(response.getOutputStream(), errorResponse);
                        return;
                    }
                }

                String sessionId = tokenProvider.getSessionIdFromToken(jwt);
                if (userDetails instanceof UserPrincipal principal) {
                    userDetails = principal.withSessionId(sessionId);
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
