package com.issuemanagement.identity.security;

import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String DEV_SECRET = JwtTokenProvider.DEFAULT_DEV_SECRET;
    private static final String STRONG_CUSTOM_SECRET = "my-super-secret-production-signing-key-that-is-at-least-256-bits-long!";
    private static final String SHORT_SECRET = "too-short-secret";

    @Test
    @DisplayName("Dev mode allows default dev secret")
    void devMode_AllowsDefaultDevSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(DEV_SECRET, 3600000, "test-issuer", "");
        User user = User.builder().id(1L).email("user@test.com").role(Role.EMPLOYEE).build();
        UserPrincipal principal = UserPrincipal.create(user);

        String token = provider.generateToken(principal);
        assertThat(token).isNotBlank();
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("user@test.com");
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Production profile rejects default dev secret")
    void prodProfile_RejectsDefaultDevSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(DEV_SECRET, 3600000, "test-issuer", "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production security violation: Default local development JWT secret is in use");
    }

    @Test
    @DisplayName("Production profile rejects short secret (< 256 bits)")
    void prodProfile_RejectsShortSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(SHORT_SECRET, 3600000, "test-issuer", "production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production security violation: JWT_SECRET must be at least 256 bits");
    }

    @Test
    @DisplayName("Production profile succeeds with strong secret")
    void prodProfile_SucceedsWithStrongSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(STRONG_CUSTOM_SECRET, 3600000, "test-issuer", "prod");
        User user = User.builder().id(2L).email("admin@company.com").role(Role.ADMIN).build();
        UserPrincipal principal = UserPrincipal.create(user);

        String token = provider.generateToken(principal);
        assertThat(token).isNotBlank();
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("admin@company.com");
    }

    @Test
    @DisplayName("Blank secret is rejected in any environment")
    void blankSecret_IsRejected() {
        assertThatThrownBy(() -> new JwtTokenProvider("", 3600000, "test-issuer", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret must not be blank");
    }
}
