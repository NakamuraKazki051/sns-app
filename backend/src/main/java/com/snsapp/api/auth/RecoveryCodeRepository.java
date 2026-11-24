package com.snsapp.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(String userId);

    /**
     * まだ使っていなければ使ったことにし、書き換えた行数を返す。
     * 確かめと書き換えを1つの UPDATE で行うので、同じコードを同時に送られても通るのは1回だけ
     */
    @Modifying
    @Query("update RecoveryCode r set r.usedAt = :at where r.id = :id and r.usedAt is null")
    int claim(@Param("id") Long id, @Param("at") Instant at);

    long countByUserIdAndUsedAtIsNull(String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecoveryCode r where r.userId = :userId")
    void deleteByUser(@Param("userId") String userId);
}
