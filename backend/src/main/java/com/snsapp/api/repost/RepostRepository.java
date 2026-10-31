package com.snsapp.api.repost;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface RepostRepository extends JpaRepository<Repost, Long> {

    boolean existsByUserIdAndPostId(String userId, Long postId);

    long countByPostId(Long postId);

    /**
     * この人たちのリポスト(新しい順)。リポストした人・元の投稿・その投稿者もまとめて読む(N+1 を避ける)。
     * before には前のページで最後に出したリポストの ID を渡す
     */
    @EntityGraph(attributePaths = {"user", "post", "post.user"})
    List<Repost> findByUserIdInOrderByIdDesc(Collection<String> userIds, Limit limit);

    @EntityGraph(attributePaths = {"user", "post", "post.user"})
    List<Repost> findByUserIdInAndIdLessThanOrderByIdDesc(Collection<String> userIds, Long before, Limit limit);

    /** 投稿ごとのリポスト数。1ページ分の投稿をまとめて数える */
    @Query("select r.post.id, count(r) from Repost r where r.post.id in :postIds group by r.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** このうち、自分がリポストしている投稿の ID */
    @Query("select r.post.id from Repost r where r.user.id = :userId and r.post.id in :postIds")
    List<Long> findRepostedPostIds(@Param("userId") String userId, @Param("postIds") Collection<Long> postIds);

    @Transactional
    @Modifying
    @Query("delete from Repost r where r.user.id = :userId and r.post.id = :postId")
    int deleteByUserIdAndPostId(@Param("userId") String userId, @Param("postId") Long postId);
}
