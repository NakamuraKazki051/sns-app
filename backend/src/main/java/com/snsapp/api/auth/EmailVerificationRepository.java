package com.snsapp.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {

    @Modifying
    @Transactional
    @Query("delete from EmailVerification v where v.userId = :userId")
    void deleteAllOfUser(@Param("userId") String userId);
}
