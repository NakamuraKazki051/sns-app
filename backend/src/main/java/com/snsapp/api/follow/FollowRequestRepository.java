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
import java.util.Optional;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    boolean existsByRequesterIdAndTargetId(String requesterId, String targetId);

    Optional<FollowRequest> findByRequesterIdAndTargetId(String requesterId, String targetId);

    /** 届いているリクエスト(新しい順)。hidden の人(ブロック中など)のものは出さない */
    @EntityGraph(attributePaths = "requester")
    List<FollowRequest> findByTargetIdAndRequesterIdNotInOrderByIdDesc(String targetId, Collection<String> hidden,
                                                                      Limit limit);

    long countByTargetIdAndRequesterIdNotIn(String targetId, Collection<String> hidden);

    @EntityGraph(attributePaths = "requester")
    List<FollowRequest> findByTargetId(String targetId);

    /** 自分がリクエストを送って、承認を待っている相手 */
    @Query("select r.targetId from FollowRequest r where r.requester.id = :me")
    List<String> findRequestedIds(@Param("me") String me);

    @Transactional
    @Modifying
    @Query("delete from FollowRequest r where r.requester.id = :requesterId and r.targetId = :targetId")
    int deleteByRequesterIdAndTargetId(@Param("requesterId") String requesterId, @Param("targetId") String targetId);
}
