package com.snsapp.api.post;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 一覧は投稿者もまとめて読む(@EntityGraph)。投稿ごとに投稿者を取りに行く N+1 を避けるため
public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "user")
    List<Post> findByOrderByIdDesc(Limit limit);

    @EntityGraph(attributePaths = "user")
    List<Post> findByIdLessThanOrderByIdDesc(Long before, Limit limit);

    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdOrderByIdDesc(String userId, Limit limit);

    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdAndIdLessThanOrderByIdDesc(String userId, Long before, Limit limit);

    long countByUserId(String userId);

    /** 自分の投稿だけ消せる。消した件数(0 か 1)を返す */
    @Transactional
    @Modifying
    @Query("delete from Post p where p.id = :id and p.user.id = :userId")
    int deleteOwn(@Param("id") Long id, @Param("userId") String userId);
}
