package com.snsapp.api.follow;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);

    long countByFollowerId(String followerId);

    long countByFolloweeId(String followeeId);

    /** フォロー中の人の ID(ホームタイムライン用) */
    @Query("select f.followee.id from Follow f where f.follower.id = :followerId")
    List<String> findFolloweeIds(@Param("followerId") String followerId);

    /**
     * おすすめ(F-27): フォロー中の人がフォローしている人と、その人をフォローしているフォロー中の人の数(多い順)。
     * excluded(自分・フォロー済み・ブロック中など)は除く。行は [ユーザー ID, 人数]
     */
    @Query("select f2.followee.id, count(f2) from Follow f1, Follow f2"
            + " where f1.follower.id = :me and f2.follower.id = f1.followee.id and f2.followee.id not in :excluded"
            + " group by f2.followee.id order by count(f2) desc, f2.followee.id")
    List<Object[]> findFollowedByFollowing(@Param("me") String me, @Param("excluded") Collection<String> excluded,
                                           Limit limit);

    /** おすすめ(F-27): フォロワーの多い人(多い順)。行は [ユーザー ID, フォロワー数] */
    @Query("select f.followee.id, count(f) from Follow f where f.followee.id not in :excluded"
            + " group by f.followee.id order by count(f) desc, f.followee.id")
    List<Object[]> findMostFollowed(@Param("excluded") Collection<String> excluded, Limit limit);

    /** フォロー中の一覧(新しくフォローした順) */
    @EntityGraph(attributePaths = "followee")
    List<Follow> findByFollowerIdAndFolloweeIdNotInOrderByIdDesc(String followerId, Collection<String> hidden, Limit limit);

    @EntityGraph(attributePaths = "followee")
    List<Follow> findByFollowerIdAndFolloweeIdNotInAndIdLessThanOrderByIdDesc(String followerId, Collection<String> hidden,
                                                                              Long before, Limit limit);

    /** フォロワーの一覧(新しくフォローされた順) */
    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFolloweeIdAndFollowerIdNotInOrderByIdDesc(String followeeId, Collection<String> hidden, Limit limit);

    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFolloweeIdAndFollowerIdNotInAndIdLessThanOrderByIdDesc(String followeeId, Collection<String> hidden,
                                                                              Long before, Limit limit);

    @Transactional
    @Modifying
    @Query("delete from Follow f where f.follower.id = :followerId and f.followee.id = :followeeId")
    int deleteByFollowerIdAndFolloweeId(@Param("followerId") String followerId, @Param("followeeId") String followeeId);
}
