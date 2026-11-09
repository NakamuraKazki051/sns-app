package com.snsapp.api.notification;

import com.snsapp.api.post.Post;
import com.snsapp.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** 通知1件(F-21)。actor が recipient に対して type をした */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type { LIKE, REPLY, REPOST, FOLLOW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String recipientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    // FOLLOW では null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    protected Notification() {
    }

    public Notification(String recipientId, User actor, Type type, Post post) {
        this.recipientId = recipientId;
        this.actor = actor;
        this.type = type;
        this.post = post;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public Type getType() {
        return type;
    }

    public Post getPost() {
        return post;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
