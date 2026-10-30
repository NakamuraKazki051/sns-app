package com.snsapp.api.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByUserIdAndPostId(String userId, Long postId);

    long countByPostId(Long postId);

    /** 投稿ごとのいいね数。1ページ分の投稿をまとめて数える */
    @Query("select l.post.id, count(l) from PostLike l where l.post.id in :postIds group by l.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** このうち、自分がいいねしている投稿の ID */
    @Query("select l.post.id from PostLike l where l.user.id = :userId and l.post.id in :postIds")
    List<Long> findLikedPostIds(@Param("userId") String userId, @Param("postIds") Collection<Long> postIds);

    @Transactional
    @Modifying
    @Query("delete from PostLike l where l.user.id = :userId and l.post.id = :postId")
    int deleteByUserIdAndPostId(@Param("userId") String userId, @Param("postId") Long postId);
}
