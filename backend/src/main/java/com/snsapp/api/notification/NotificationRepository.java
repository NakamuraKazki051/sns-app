package com.snsapp.api.notification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 自分の通知(新しい順)。ブロック・ミュート中の人からのものは除く。した人と投稿もまとめて読む */
    @EntityGraph(attributePaths = {"actor", "post"})
    List<Notification> findByRecipientIdAndActorIdNotInOrderByIdDesc(String recipientId, Collection<String> hidden,
                                                                     Limit limit);

    @EntityGraph(attributePaths = {"actor", "post"})
    List<Notification> findByRecipientIdAndActorIdNotInAndIdLessThanOrderByIdDesc(String recipientId,
                                                                                  Collection<String> hidden,
                                                                                  Long before, Limit limit);

    long countByRecipientIdAndActorIdNotInAndReadAtIsNull(String recipientId, Collection<String> hidden);

    /** 取り消されたら消す(いいねを外した・フォローを外したなど) */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Notification n where n.recipientId = :recipientId and n.actor.id = :actorId and n.type = :type"
            + " and ((:postId is null and n.post is null) or n.post.id = :postId)")
    void deleteMatching(@Param("recipientId") String recipientId, @Param("actorId") String actorId,
                        @Param("type") Notification.Type type, @Param("postId") Long postId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :at where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") String recipientId, @Param("at") Instant at);
}
