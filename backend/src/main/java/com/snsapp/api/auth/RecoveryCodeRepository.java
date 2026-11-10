package com.snsapp.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(String userId);

    long countByUserIdAndUsedAtIsNull(String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecoveryCode r where r.userId = :userId")
    void deleteByUser(@Param("userId") String userId);
}
