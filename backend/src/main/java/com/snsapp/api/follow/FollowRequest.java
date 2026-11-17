package com.snsapp.api.follow;

import com.snsapp.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** 鍵アカウント(F-26)へのフォローのリクエスト。承認されたら Follow に移す(V17__private_accounts.sql) */
@Entity
@Table(name = "follow_requests")
public class FollowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Column(nullable = false)
    private Instant createdAt;

    protected FollowRequest() {
    }

    public FollowRequest(User requester, String targetId) {
        this.requester = requester;
        this.targetId = targetId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public String getTargetId() {
        return targetId;
    }
}
