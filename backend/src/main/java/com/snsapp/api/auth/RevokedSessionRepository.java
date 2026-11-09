package com.snsapp.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface RevokedSessionRepository extends JpaRepository<RevokedSession, String> {

    /** 期限を過ぎた行を消す。その ID の JWT はもう期限切れで断られるので、覚えておく必要がない */
    @Modifying
    @Transactional
    @Query("delete from RevokedSession r where r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
