package com.snsapp.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    /** 今も使えるログイン(パスワードを変える前のもの・期限切れは除く)。最後に使った新しい順 */
    @Query("select s from UserSession s where s.userId = :userId and s.tokenVersion = :version"
            + " and s.expiresAt > :now order by s.lastUsedAt desc")
    List<UserSession> findActive(@Param("userId") String userId, @Param("version") int version,
                                 @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserSession s where s.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
