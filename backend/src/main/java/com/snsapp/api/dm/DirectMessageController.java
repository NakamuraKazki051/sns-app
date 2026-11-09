package com.snsapp.api.dm;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.common.TextLength;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.PublicUser;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DM(F-23)。ブロックしている・されている人とは送り合えない(相手は 404)。ミュートは DM に関係しない。
 * 新着は画面が聞きに来る(会話を開いている間は最初のページを5秒ごと、未読の数は30秒ごと)
 */
@RestController
@RequestMapping("/api/dm")
@Tag(name = OpenApiConfig.TAG_DM)
public class DirectMessageController {

    static final int PAGE_SIZE = 30;
    static final int CONVERSATIONS_MAX = 50;
    static final int SENDS_PER_WINDOW = 20;
    private static final Duration SEND_WINDOW = Duration.ofMinutes(1);
    // 見た目の文字数を数える前に、とても長い本文を断る
    private static final int BODY_MAX_CHARS = 4000;

    public record SendRequest(@Schema(description = "本文。500字まで(絵文字も1字と数える)") String body) {
    }

    public record MessageResponse(@Schema(description = "メッセージ ID(文字列)") String id,
                                  @Schema(description = "自分が送ったものか") boolean mine,
                                  String body, Instant createdAt,
                                  @Schema(description = "受け取った人が読んだか") boolean read) {
        static MessageResponse of(DirectMessage m, String me) {
            return new MessageResponse(m.getId().toString(), m.getSenderId().equals(me), m.getBody(), m.getCreatedAt(),
                    m.getReadAt() != null);
        }
    }

    public record MessagePage(@Schema(description = "新しい順") List<MessageResponse> messages, boolean hasMore,
                              @Schema(types = {"string", "null"}, description = "古いものを読むときに before に渡す値") String nextCursor) {
    }

    public record Conversation(PublicUser partner, MessageResponse lastMessage, long unreadCount) {
    }

    public record UnreadCount(long count) {
    }

    private final DirectMessageRepository messages;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final Visibility visibility;
    private final RateLimiter rateLimiter;

    public DirectMessageController(DirectMessageRepository messages, UserRepository users, CurrentUser currentUser,
                                   Visibility visibility, RateLimiter rateLimiter) {
        this.messages = messages;
        this.users = users;
        this.currentUser = currentUser;
        this.visibility = visibility;
        this.rateLimiter = rateLimiter;
    }

    @Operation(operationId = "listConversations", summary = "DM の会話一覧",
            description = "最後のメッセージが新しい会話から50件。相手、最後のメッセージ、未読の数")
    @GetMapping
    public List<Conversation> conversations(HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        Set<String> hidden = visibility.blockRelated(me);
        List<Long> latestIds = messages.findLatestIdsPerPartner(me, hidden, Limit.of(CONVERSATIONS_MAX));
        if (latestIds.isEmpty()) {
            return List.of();
        }
        Map<Long, DirectMessage> latest = messages.findAllById(latestIds).stream()
                .collect(Collectors.toMap(DirectMessage::getId, Function.identity()));
        Map<String, Long> unread = new HashMap<>();
        for (Object[] row : messages.countUnreadPerPartner(me, hidden)) {
            unread.put((String) row[0], (Long) row[1]);
        }
        Map<String, User> partners = users.findAllById(
                        latest.values().stream().map(m -> m.partnerOf(me)).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return latestIds.stream()
                .map(latest::get)
                .filter(m -> partners.containsKey(m.partnerOf(me)))
                .map(m -> new Conversation(PublicUser.from(partners.get(m.partnerOf(me))), MessageResponse.of(m, me),
                        unread.getOrDefault(m.partnerOf(me), 0L)))
                .toList();
    }

    @Operation(operationId = "countUnreadMessages", summary = "読んでいない DM の数")
    @GetMapping("/unread-count")
    public UnreadCount unreadCount(HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        return new UnreadCount(messages.countByRecipientIdAndReadAtIsNullAndSenderIdNotIn(me, visibility.blockRelated(me)));
    }

    @Operation(operationId = "listMessages", summary = "相手との DM",
            description = "新しい順に30件。開くと、相手から届いた分は既読になる。会話を開いている間、画面が5秒ごとに最初のページを聞き直す(新着と既読を知るため)")
    @ApiErrors({400, 404})
    @GetMapping("/{username}")
    public MessagePage list(@PathVariable String username,
                            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false) Long before,
                            HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        User partner = partner(username, me);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<DirectMessage> fetched = before == null
                ? messages.findBetween(me, partner.getId(), limit)
                : messages.findBetweenBefore(me, partner.getId(), before, limit);
        if (before == null) {
            messages.markRead(me, partner.getId(), Instant.now());
        }
        List<DirectMessage> page = fetched.stream().limit(PAGE_SIZE).toList();
        boolean hasMore = fetched.size() > PAGE_SIZE;
        return new MessagePage(page.stream().map(m -> MessageResponse.of(m, me)).toList(), hasMore,
                hasMore ? page.getLast().getId().toString() : null);
    }

    @Operation(operationId = "sendMessage", summary = "DM を送る",
            description = "本文は500字まで(絵文字も1字と数える)。自分には送れない(400)。1人1分に20通まで")
    @ApiErrors({400, 404, 429})
    @PostMapping("/{username}")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable String username, @RequestBody SendRequest request,
                                HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        User partner = partner(username, me);
        if (partner.getId().equals(me)) {
            throw ApiException.badRequest("自分にはメッセージを送れません");
        }
        String body = request.body() == null ? "" : request.body().strip();
        if (body.isEmpty()) {
            throw ApiException.badRequest("メッセージを入力してください");
        }
        if (body.length() > BODY_MAX_CHARS || TextLength.visible(body) > DirectMessage.BODY_MAX) {
            throw ApiException.badRequest("メッセージは" + DirectMessage.BODY_MAX + "文字以内で入力してください");
        }
        if (!rateLimiter.tryAcquire("dm:" + me, SENDS_PER_WINDOW, SEND_WINDOW)) {
            throw ApiException.tooManyRequests("メッセージが多すぎます。1分ほど待ってからもう一度お試しください");
        }
        return MessageResponse.of(messages.save(new DirectMessage(me, partner.getId(), body)), me);
    }

    // ブロックしている・されている相手は、いないものとして扱う
    private User partner(String username, String me) {
        return (username.matches(User.USERNAME_PATTERN) ? users.findByUsername(username) : Optional.<User>empty())
                .filter(User::isEmailVerified)
                .filter(user -> !visibility.blockedBetween(me, user.getId()))
                .orElseThrow(() -> ApiException.notFound("ユーザーが見つかりません: @" + username));
    }
}
