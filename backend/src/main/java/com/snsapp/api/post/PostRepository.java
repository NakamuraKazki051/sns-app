package com.snsapp.api.post;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    @EntityGraph(attributePaths = "user")
    Optional<Post> findWithUserById(Long id);

    /** ホームタイムライン: 自分とフォロー中の人の投稿 */
    @EntityGraph(attributePaths = "user")
    @Query("select p from Post p where p.user.id = :userId"
            + " or p.user.id in (select f.followee.id from Follow f where f.follower.id = :userId)"
            + " order by p.id desc")
    List<Post> findHome(@Param("userId") String userId, Limit limit);

    @EntityGraph(attributePaths = "user")
    @Query("select p from Post p where p.id < :before and (p.user.id = :userId"
            + " or p.user.id in (select f.followee.id from Follow f where f.follower.id = :userId))"
            + " order by p.id desc")
    List<Post> findHomeBefore(@Param("userId") String userId, @Param("before") Long before, Limit limit);

    long countByUserId(String userId);

    /** 自分の投稿だけ消せる。消した件数(0 か 1)を返す */
    @Transactional
    @Modifying
    @Query("delete from Post p where p.id = :id and p.user.id = :userId")
    int deleteOwn(@Param("id") Long id, @Param("userId") String userId);
}
