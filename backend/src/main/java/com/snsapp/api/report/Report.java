package com.snsapp.api.report;

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

/** 通報1件(V16__reports.sql)。投稿の通報なら post が入る */
@Entity
@Table(name = "reports")
public class Report {

    /** 通報の理由。画面の選択肢と同じ */
    public enum Reason {
        /** スパム・宣伝 */
        SPAM,
        /** 嫌がらせ・攻撃的な内容 */
        HARASSMENT,
        /** 不適切な画像・内容 */
        INAPPROPRIATE,
        /** なりすまし */
        IMPERSONATION,
        /** その他 */
        OTHER
    }

    /** 管理者の対応 */
    public enum Resolution {
        /** 問題なし(何もしない) */
        DISMISSED,
        /** 投稿を消した */
        POST_DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    // 通報した時点の本文の写し。投稿が消されても何を通報したか分かるように
    @Column(length = 2000)
    private String postBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Resolution resolution;

    protected Report() {
    }

    public Report(User reporter, User targetUser, Post post, Reason reason, String detail) {
        this.reporter = reporter;
        this.targetUser = targetUser;
        this.post = post;
        this.postBody = post == null ? null : post.getBody();
        this.reason = reason;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public void resolve(Resolution resolution) {
        this.resolution = resolution;
        this.resolvedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getReporter() {
        return reporter;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public Post getPost() {
        return post;
    }

    public String getPostBody() {
        return postBody;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Resolution getResolution() {
        return resolution;
    }
}
