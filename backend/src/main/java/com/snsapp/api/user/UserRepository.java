package com.snsapp.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    // ユーザー名は大文字・小文字を区別しない(DB の照合順序による)
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
}
