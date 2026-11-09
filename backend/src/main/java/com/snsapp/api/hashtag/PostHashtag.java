package com.snsapp.api.hashtag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 投稿に付いたハッシュタグ1つ(F-22) */
@Entity
@Table(name = "post_hashtags")
public class PostHashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false, length = Hashtags.TAG_MAX)
    private String tag;

    // 投稿した時刻(直しても変えない)
    @Column(nullable = false)
    private Instant createdAt;

    protected PostHashtag() {
    }

    public PostHashtag(Long postId, String tag, Instant createdAt) {
        this.postId = postId;
        this.tag = tag;
        this.createdAt = createdAt;
    }
}
