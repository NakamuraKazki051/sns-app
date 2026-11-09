package com.snsapp.api.dm;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    String BETWEEN = "((m.senderId = :me and m.recipientId = :other) or (m.senderId = :other and m.recipientId = :me))";

    /** 2人の間のメッセージ(新しい順) */
    @Query("select m from DirectMessage m where " + BETWEEN + " order by m.id desc")
    List<DirectMessage> findBetween(@Param("me") String me, @Param("other") String other, Limit limit);

    @Query("select m from DirectMessage m where " + BETWEEN + " and m.id < :before order by m.id desc")
    List<DirectMessage> findBetweenBefore(@Param("me") String me, @Param("other") String other,
                                          @Param("before") Long before, Limit limit);

    /** 会話ごとの最新のメッセージの ID(新しい会話から)。hidden の人との会話は除く */
    @Query("select max(m.id) from DirectMessage m"
            + " where (m.senderId = :me and m.recipientId not in :hidden) or (m.recipientId = :me and m.senderId not in :hidden)"
            + " group by case when m.senderId = :me then m.recipientId else m.senderId end"
            + " order by max(m.id) desc")
    List<Long> findLatestIdsPerPartner(@Param("me") String me, @Param("hidden") Collection<String> hidden, Limit limit);

    /** 相手ごとの、自分が読んでいない数 */
    @Query("select m.senderId, count(m) from DirectMessage m where m.recipientId = :me and m.readAt is null"
            + " and m.senderId not in :hidden group by m.senderId")
    List<Object[]> countUnreadPerPartner(@Param("me") String me, @Param("hidden") Collection<String> hidden);

    long countByRecipientIdAndReadAtIsNullAndSenderIdNotIn(String recipientId, Collection<String> hidden);

    /** 相手から届いた分を既読にする */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DirectMessage m set m.readAt = :at where m.recipientId = :me and m.senderId = :other and m.readAt is null")
    int markRead(@Param("me") String me, @Param("other") String other, @Param("at") Instant at);
}
