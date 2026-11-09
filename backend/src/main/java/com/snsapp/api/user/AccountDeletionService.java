package com.snsapp.api.user;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * アカウントを、その人が作ったものと一緒に消す(F-17、docs/spec.md の 4.1)。
 * 1つのトランザクションで消すので、途中で失敗しても半分だけ消えた状態にはならない。
 * 新しいテーブルで users を参照するときは ON DELETE CASCADE にするか、ここに消す順番を足す
 */
@Service
public class AccountDeletionService {

    private final EntityManager entityManager;

    public AccountDeletionService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 消して、その人の投稿の画像の置き場所の名前を返す(画像は DB の外にあるので、呼んだ側が消す) */
    @Transactional
    public List<String> delete(String userId) {
        // まだ DB に書いていない変更があれば先に書く(SQL で直接消すので、あとから書かれると食い違う)
        entityManager.flush();
        @SuppressWarnings("unchecked")
        List<String> imageKeys = entityManager.createNativeQuery(
                        "select image_key from posts where user_id = :id and image_key is not null")
                .setParameter("id", userId)
                .getResultList();
        // 他の人の投稿に付けたもの
        run("delete from likes where user_id = :id", userId);
        run("delete from reposts where user_id = :id", userId);
        run("delete from replies where user_id = :id", userId);
        run("delete from follows where follower_id = :id or followee_id = :id", userId);
        // 自分の投稿。付いたリプライ・いいね・リポストは DB が一緒に消す(ON DELETE CASCADE)
        run("delete from posts where user_id = :id", userId);
        run("delete from user_icons where user_id = :id", userId);
        // 確認メールの控えなど、ON DELETE CASCADE のものは DB が一緒に消す
        run("delete from users where id = :id", userId);
        // SQL で直接消したので、読み込み済みのエンティティ(消したユーザーなど)を忘れさせる
        entityManager.clear();
        return imageKeys;
    }

    private void run(String sql, String userId) {
        entityManager.createNativeQuery(sql).setParameter("id", userId).executeUpdate();
    }
}
