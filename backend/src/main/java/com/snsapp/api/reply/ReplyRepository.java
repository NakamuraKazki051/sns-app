package com.snsapp.api.reply;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

    @EntityGraph(attributePaths = "user")
    List<Reply> findByPostIdOrderByIdAsc(Long postId, Limit limit);

    @EntityGraph(attributePaths = "user")
    List<Reply> findByPostIdAndIdGreaterThanOrderByIdAsc(Long postId, Long after, Limit limit);

    /** 投稿ごとのリプライ数。1ページ分の投稿をまとめて数える */
    @Query("select r.post.id, count(r) from Reply r where r.post.id in :postIds group by r.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** 自分のリプライだけ消せる。消した件数(0 か 1)を返す */
    @Transactional
    @Modifying
    @Query("delete from Reply r where r.id = :id and r.user.id = :userId")
    int deleteOwn(@Param("id") Long id, @Param("userId") String userId);
}
