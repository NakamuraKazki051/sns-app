package com.snsapp.api.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * 行をロックして読む(SELECT ... FOR UPDATE)。同じトークンの取り替えやログアウトが同時に来ても、
     * 片方が終わるまでもう片方を待たせ、終わったあとの最新の状態で判断する
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(@Param("hash") String hash);

    /** そのログインのリフレッシュトークンを、使ったものも含めてすべて消す */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken r where r.sessionId = :sessionId")
    void deleteBySession(@Param("sessionId") String sessionId);

    /** 期限を過ぎた行を消す。もう使えないので、使い回しを見張る必要もない */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
