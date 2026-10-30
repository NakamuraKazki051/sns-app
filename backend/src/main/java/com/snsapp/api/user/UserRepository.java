package com.snsapp.api.user;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    // ユーザー名は大文字・小文字を区別しない(DB の照合順序による)
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    /**
     * ユーザー名か表示名の一部で探す。ユーザー名順に並べ、after(ユーザー名)より後ろから読む。
     * pattern は %〜% で囲み、% と _ と \ をエスケープ済みのもの
     */
    @Query("select u from User u where (u.username like :pattern escape '\\' or u.displayName like :pattern escape '\\')"
            + " and u.username > :after order by u.username")
    List<User> search(@Param("pattern") String pattern, @Param("after") String after, Limit limit);
}
