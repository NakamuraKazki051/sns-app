package com.snsapp.api.post;

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

// 一覧は投稿者もまとめて読む(@EntityGraph)。投稿ごとに投稿者を取りに行く N+1 を避けるため
public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdNotInOrderByIdDesc(Collection<String> hiddenUserIds, Limit limit);

    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdNotInAndIdLessThanOrderByIdDesc(Collection<String> hiddenUserIds, Long before, Limit limit);

    @EntityGraph(attributePaths = "user")
    Optional<Post> findWithUserById(Long id);

    /**
     * 渡した投稿者の投稿(ホームタイムラインは自分とフォロー中の人、ユーザーページはその人1人)。
     * 「自分 or フォロー中(サブクエリ)」の条件だと MySQL が投稿者の索引を使えず、投稿を新しい順に全件見ていく。
     * 該当する投稿が少ない人ほど遅くなる(投稿10万件・フォロー0人で約0.5秒)ので、投稿者の ID を先に取って IN で渡す
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdInOrderByIdDesc(Collection<String> userIds, Limit limit);

    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdInAndIdLessThanOrderByIdDesc(Collection<String> userIds, Long before, Limit limit);

    long countByUserId(String userId);

    /*
     * 新着のお知らせ(全体タイムライン): after より新しい、自分以外の投稿。
     * ID(主キー)の範囲で読むので、新着の分しか見ない。数えるのは上限まで(何千件あっても数えきらない)
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findByIdGreaterThanAndUserIdNotInOrderByIdDesc(Long after, Collection<String> excludedUserIds, Limit limit);

    /** 新着のお知らせ(ホームタイムライン): after より新しい、フォロー中の人の投稿 */
    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdInAndIdGreaterThanOrderByIdDesc(Collection<String> userIds, Long after, Limit limit);

    /** 自分の投稿だけ読む(消す前に画像の置き場所を知る、本文を直す) */
    Optional<Post> findByIdAndUserId(Long id, String userId);

    /** 自分の投稿だけ消せる。消した件数(0 か 1)を返す */
    @Transactional
    @Modifying
    @Query("delete from Post p where p.id = :id and p.user.id = :userId")
    int deleteOwn(@Param("id") Long id, @Param("userId") String userId);

    @EntityGraph(attributePaths = "user")
    List<Post> findByIdIn(Collection<Long> ids);

    /**
     * おすすめの投稿(F-27)。新しい投稿 candidates 件のうち since より後のものを、
     * いいね + リポスト×2 + リプライ の多い順に limit 件。hidden の人の投稿は除く。
     * 候補を新しいものに絞るので、投稿が増えても数える量は増えない(いいね・リポスト・リプライは post_id の索引で数える)
     */
    @Query(value = "select p.id from (select id from posts where user_id not in (:hidden) order by id desc limit :candidates) c"
            + " join posts p on p.id = c.id where p.created_at > :since"
            + " order by ((select count(*) from likes l where l.post_id = p.id)"
            + " + 2 * (select count(*) from reposts r where r.post_id = p.id)"
            + " + (select count(*) from replies x where x.post_id = p.id)) desc, p.id desc limit :limit",
            nativeQuery = true)
    List<Long> findRecommendedIds(@Param("hidden") Collection<String> hidden, @Param("since") java.time.Instant since,
                                  @Param("candidates") int candidates, @Param("limit") int limit);

    /** 管理者が通報を受けて消す(誰の投稿でも)。消した件数を返す */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Post p where p.id = :id")
    int deleteAnyById(@Param("id") Long id);

    /** ハッシュタグの付いた投稿(新しい順、F-22)。hidden の人の投稿は除く */
    @Query("select p from Post p join fetch p.user where p.id in"
            + " (select h.postId from PostHashtag h where h.tag = :tag)"
            + " and p.user.id not in :hidden order by p.id desc")
    List<Post> findByHashtag(@Param("tag") String tag, @Param("hidden") Collection<String> hidden, Limit limit);

    @Query("select p from Post p join fetch p.user where p.id in"
            + " (select h.postId from PostHashtag h where h.tag = :tag and h.postId < :before)"
            + " and p.user.id not in :hidden order by p.id desc")
    List<Post> findByHashtagBefore(@Param("tag") String tag, @Param("hidden") Collection<String> hidden,
                                   @Param("before") Long before, Limit limit);
}
