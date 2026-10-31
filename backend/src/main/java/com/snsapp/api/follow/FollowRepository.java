package com.snsapp.api.follow;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);

    long countByFollowerId(String followerId);

    long countByFolloweeId(String followeeId);

    /** フォロー中の人の ID(ホームタイムライン用) */
    @Query("select f.followee.id from Follow f where f.follower.id = :followerId")
    List<String> findFolloweeIds(@Param("followerId") String followerId);

    /** フォロー中の一覧(新しくフォローした順) */
    @EntityGraph(attributePaths = "followee")
    List<Follow> findByFollowerIdOrderByIdDesc(String followerId, Limit limit);

    @EntityGraph(attributePaths = "followee")
    List<Follow> findByFollowerIdAndIdLessThanOrderByIdDesc(String followerId, Long before, Limit limit);

    /** フォロワーの一覧(新しくフォローされた順) */
    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFolloweeIdOrderByIdDesc(String followeeId, Limit limit);

    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFolloweeIdAndIdLessThanOrderByIdDesc(String followeeId, Long before, Limit limit);

    @Transactional
    @Modifying
    @Query("delete from Follow f where f.follower.id = :followerId and f.followee.id = :followeeId")
    int deleteByFollowerIdAndFolloweeId(@Param("followerId") String followerId, @Param("followeeId") String followeeId);
}
