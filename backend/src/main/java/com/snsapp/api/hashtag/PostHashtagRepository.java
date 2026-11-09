package com.snsapp.api.hashtag;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    @Transactional
    // 投稿の編集の途中でも呼ぶので、読み込み済みのエンティティは忘れさせない(タグのエンティティは読まない)
    @Modifying(flushAutomatically = true)
    @Query("delete from PostHashtag h where h.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    /** since より後に投稿されたタグを、使われた投稿の多い順に。同じ数ならタグの名前順 */
    @Query("select h.tag, count(h) from PostHashtag h where h.createdAt > :since"
            + " group by h.tag order by count(h) desc, h.tag")
    List<Object[]> countSince(@Param("since") Instant since, Limit limit);
}
