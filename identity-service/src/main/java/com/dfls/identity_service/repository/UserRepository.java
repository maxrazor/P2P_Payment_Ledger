package com.dfls.identity_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dfls.identity_service.model.User;

public interface UserRepository extends JpaRepository<User, UUID>{
    Optional<User> findByUsername(String username);

    boolean existsByEmail(String username);

    Optional<User> findByEmail(String email);
}
