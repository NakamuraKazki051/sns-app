package com.snsapp.api.notification;

import com.snsapp.api.block.Visibility;
import com.snsapp.api.post.Post;
import com.snsapp.api.user.UserRepository;
import org.springframework.stereotype.Service;

/**
 * 通知を作る・消す(F-21)。いいね・リプライ・リポスト・フォローの各 API から呼ぶ。
 * 自分への反応と、ブロックしている・されている相手からの反応は通知しない
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Visibility visibility;

    public NotificationService(NotificationRepository notifications, UserRepository users, Visibility visibility) {
        this.notifications = notifications;
        this.users = users;
        this.visibility = visibility;
    }

    /** 投稿への反応(いいね・リプライ・リポスト)。投稿した人に知らせる */
    public void postActivity(Notification.Type type, String actorId, Post post) {
        notify(post.getUser().getId(), actorId, type, post);
    }

    public void followed(String actorId, String followeeId) {
        notify(followeeId, actorId, Notification.Type.FOLLOW, null);
    }

    /** いいね・リポスト・フォローを取り消したら、その通知も消す(付けたり外したりで通知が溜まらないように) */
    public void retract(Notification.Type type, String actorId, String recipientId, Long postId) {
        notifications.deleteMatching(recipientId, actorId, type, postId);
    }

    private void notify(String recipientId, String actorId, Notification.Type type, Post post) {
        if (recipientId.equals(actorId) || visibility.blockedBetween(recipientId, actorId)) {
            return;
        }
        // 付け直しのたびに同じ通知が増えないよう、前のものは消してから作る(リプライは毎回別の通知)
        if (type != Notification.Type.REPLY) {
            notifications.deleteMatching(recipientId, actorId, type, post == null ? null : post.getId());
        }
        notifications.save(new Notification(recipientId, users.getReferenceById(actorId), type, post));
    }
}
