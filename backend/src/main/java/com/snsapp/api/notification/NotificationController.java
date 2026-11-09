package com.snsapp.api.notification;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.post.Post;
import com.snsapp.api.user.UserDtos.PublicUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 通知(F-21)。画面は未読の数を30秒ごとに聞きに来る */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = OpenApiConfig.TAG_NOTIFICATION)
public class NotificationController {

    static final int PAGE_SIZE = 20;

    /** 通知に添える投稿の中身(本文の先頭だけ画面に出す) */
    public record PostSummary(@Schema(description = "投稿 ID(文字列)") String id, String body) {
    }

    public record NotificationResponse(@Schema(description = "通知 ID(文字列)") String id,
                                       @Schema(allowableValues = {"LIKE", "REPLY", "REPOST", "FOLLOW"}) String type,
                                       PublicUser actor,
                                       @Schema(types = {"object", "null"}, description = "どの投稿への反応か。フォローでは null") PostSummary post,
                                       Instant createdAt, boolean read) {
        static NotificationResponse from(Notification n) {
            Post post = n.getPost();
            return new NotificationResponse(n.getId().toString(), n.getType().name(), PublicUser.from(n.getActor()),
                    post == null ? null : new PostSummary(post.getId().toString(), post.getBody()),
                    n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    public record NotificationPage(List<NotificationResponse> notifications, boolean hasMore,
                                   @Schema(types = {"string", "null"}, description = "次のページを読むときに before に渡す値") String nextCursor) {
    }

    public record UnreadCount(long count) {
    }

    private final NotificationRepository notifications;
    private final CurrentUser currentUser;
    private final Visibility visibility;

    public NotificationController(NotificationRepository notifications, CurrentUser currentUser, Visibility visibility) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.visibility = visibility;
    }

    @Operation(operationId = "listNotifications", summary = "自分への通知",
            description = "新しい順に20件。ブロック・ミュート中の人からのものは出さない")
    @ApiErrors(400)
    @GetMapping
    public NotificationPage list(@Parameter(description = "前のページの nextCursor") @RequestParam(required = false) Long before,
                                 HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Set<String> hidden = visibility.hidden(userId);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Notification> fetched = before == null
                ? notifications.findByRecipientIdAndActorIdNotInOrderByIdDesc(userId, hidden, limit)
                : notifications.findByRecipientIdAndActorIdNotInAndIdLessThanOrderByIdDesc(userId, hidden, before, limit);
        List<Notification> page = fetched.stream().limit(PAGE_SIZE).toList();
        boolean hasMore = fetched.size() > PAGE_SIZE;
        return new NotificationPage(page.stream().map(NotificationResponse::from).toList(), hasMore,
                hasMore ? page.getLast().getId().toString() : null);
    }

    @Operation(operationId = "countUnreadNotifications", summary = "未読の通知の数")
    @GetMapping("/unread-count")
    public UnreadCount unreadCount(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return new UnreadCount(notifications.countByRecipientIdAndActorIdNotInAndReadAtIsNull(userId,
                visibility.hidden(userId)));
    }

    @Operation(operationId = "markNotificationsRead", summary = "通知をすべて既読にする",
            description = "通知の画面を開いたときに呼ぶ")
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(HttpServletRequest httpRequest) {
        notifications.markAllRead(currentUser.require(httpRequest), Instant.now());
    }
}
