package com.snsapp.api.dm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** DM 1通(F-23) */
@Entity
@Table(name = "direct_messages")
public class DirectMessage {

    public static final int BODY_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String senderId;

    @Column(nullable = false, length = 36)
    private String recipientId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    protected DirectMessage() {
    }

    public DirectMessage(String senderId, String recipientId, String body) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    /** 会話の相手(me から見て) */
    public String partnerOf(String me) {
        return senderId.equals(me) ? recipientId : senderId;
    }
}
