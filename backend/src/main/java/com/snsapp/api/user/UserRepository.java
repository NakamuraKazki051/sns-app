package com.snsapp.api.user;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    // ユーザー名は大文字・小文字を区別しない(DB の照合順序による)
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    /** 確認されなかった登録を消す。ログインできないので、投稿などは持っていない */
    @Modifying
    @Transactional
    @Query("delete from User u where u.emailVerifiedAt is null and u.createdAt < :before")
    void deleteUnverifiedBefore(@Param("before") Instant before);

    /**
     * 認証アプリのコードの時間枠を使ったことにする(F-24)。前に使った枠より新しいときだけ書き換え、書き換えた行数を返す。
     * 確かめと書き換えを1つの UPDATE で行うので、同じコードを同時に送られても通るのは1回だけ
     */
    @Modifying
    @Query("update User u set u.totpLastStep = :step where u.id = :id and u.totpLastStep < :step")
    int claimTotpStep(@Param("id") String id, @Param("step") long step);

    /** おすすめ(F-27): 最近登録した人(メールの確認が済んだ人だけ、新しい順) */
    @Query("select u.id from User u where u.emailVerifiedAt is not null and u.id not in :excluded order by u.createdAt desc")
    List<String> findNewestVerifiedIds(@Param("excluded") Collection<String> excluded, Limit limit);

    /** 鍵アカウント(F-26)のうち、viewer がフォローしていない人(viewer 本人は除く)。この人たちの投稿は viewer に見えない */
    @Query("select u.id from User u where u.privateAccount = true and u.id <> :viewer"
            + " and u.id not in (select f.followee.id from Follow f where f.follower.id = :viewer)")
    List<String> findPrivateNotFollowedBy(@Param("viewer") String viewer);

    /**
     * ユーザー名か表示名の一部で探す。ユーザー名順に並べ、after(ユーザー名)より後ろから読む。
     * pattern は %〜% で囲み、% と _ と \ をエスケープ済みのもの。メールの確認が済んでいない登録は出さない
     */
    @Query("select u from User u where (u.username like :pattern escape '\\' or u.displayName like :pattern escape '\\')"
            + " and u.emailVerifiedAt is not null and u.username > :after and u.id not in :hidden order by u.username")
    List<User> search(@Param("pattern") String pattern, @Param("after") String after,
                      @Param("hidden") Collection<String> hidden, Limit limit);
}
