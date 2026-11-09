package com.snsapp.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, String> {

    // 消したリンクを、読み込み済みのものからも忘れさせる(同じトランザクションの中で古いリンクが使えてしまわないように)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from PasswordReset r where r.userId = :userId")
    void deleteAllOfUser(@Param("userId") String userId);
}
