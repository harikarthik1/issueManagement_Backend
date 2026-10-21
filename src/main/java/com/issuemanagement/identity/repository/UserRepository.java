package com.issuemanagement.identity.repository;

import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByRoleAndActiveTrue(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    boolean existsByRole(Role role);

    long countByRole(Role role);
}
