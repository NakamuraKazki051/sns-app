package com.snsapp.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * まだ使っていなければ「使った」にする。変えた行の数を返す(0なら、もう使われていた)。
     * 読んでから書くと、同時に届いた2つがどちらも取り替えられてしまうので、1つの UPDATE で確かめて書く
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.usedAt = :now where r.tokenHash = :hash and r.usedAt is null")
    int markUsed(@Param("hash") String hash, @Param("now") Instant now);

    /** そのログインのリフレッシュトークンを、使ったものも含めてすべて消す */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken r where r.sessionId = :sessionId")
    void deleteBySession(@Param("sessionId") String sessionId);

    /** 期限を過ぎた行を消す。もう使えないので、使い回しを見張る必要もない */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
