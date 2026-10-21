package com.issuemanagement.identity.security;

import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final Role role;
    private final boolean active;
    private final boolean passwordChangeRequired;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String sessionId;

    public static UserPrincipal create(User user) {
        return create(user, null);
    }

    public static UserPrincipal create(User user, String sessionId) {
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .active(user.isActive())
                .passwordChangeRequired(user.isPasswordChangeRequired())
                .authorities(Collections.singletonList(authority))
                .sessionId(sessionId)
                .build();
    }

    public UserPrincipal withSessionId(String sessionId) {
        return UserPrincipal.builder()
                .id(this.id)
                .email(this.email)
                .password(this.password)
                .firstName(this.firstName)
                .lastName(this.lastName)
                .role(this.role)
                .active(this.active)
                .passwordChangeRequired(this.passwordChangeRequired)
                .authorities(this.authorities)
                .sessionId(sessionId)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
