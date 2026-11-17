package com.snsapp.api.dm;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.common.TextLength;
import com.snsapp.api.post.ImageStorage;
import com.snsapp.api.post.PostDtos.ImageResponse;
import com.snsapp.api.post.PostImage;
import com.snsapp.api.post.UploadedImage;
import com.snsapp.api.realtime.RealtimeHub;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.PublicUser;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private static final Logger log = LoggerFactory.getLogger(DirectMessageController.class);

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
                                  @Schema(description = "本文。画像だけのメッセージなら空") String body, Instant createdAt,
                                  @Schema(description = "受け取った人が読んだか") boolean read,
                                  @Schema(types = {"object", "null"}, description = "画像。無ければ null") ImageResponse image) {
        static MessageResponse of(DirectMessage m, String me) {
            PostImage image = m.getImage();
            return new MessageResponse(m.getId().toString(), m.getSenderId().equals(me), m.getBody(), m.getCreatedAt(),
                    m.getReadAt() != null, image == null ? null
                    : new ImageResponse("/api/dm/messages/" + m.getId() + "/image", image.width(), image.height()));
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
    private final ImageStorage imageStorage;
    private final RealtimeHub realtime;

    public DirectMessageController(DirectMessageRepository messages, UserRepository users, CurrentUser currentUser,
                                   Visibility visibility, RateLimiter rateLimiter, ImageStorage imageStorage,
                                   RealtimeHub realtime) {
        this.imageStorage = imageStorage;
        this.realtime = realtime;
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
            // 読んだことを相手の画面に知らせる(「既読」を付ける)
            if (messages.markRead(me, partner.getId(), Instant.now()) > 0) {
                realtime.send(partner.getId(), RealtimeHub.Event.DM, usernameOf(me));
            }
        }
        List<DirectMessage> page = fetched.stream().limit(PAGE_SIZE).toList();
        boolean hasMore = fetched.size() > PAGE_SIZE;
        return new MessagePage(page.stream().map(m -> MessageResponse.of(m, me)).toList(), hasMore,
                hasMore ? page.getLast().getId().toString() : null);
    }

    @Operation(operationId = "sendMessage", summary = "DM を送る",
            description = "本文は500字まで(絵文字も1字と数える)。自分には送れない(400)。1人1分に20通まで。"
                    + "画像を付けるときは multipart/form-data で image(JPEG か PNG、5MB まで)と body(空でもよい)を送る")
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
        String body = bodyOf(request.body(), false);
        acquireSendSlot(me);
        DirectMessage saved = messages.save(new DirectMessage(me, partner.getId(), body));
        realtime.send(partner.getId(), RealtimeHub.Event.DM, usernameOf(me));
        return MessageResponse.of(saved, me);
    }

    /** 画像付きの DM(multipart/form-data)。画像があれば本文は空でもよい */
    @Hidden // 仕様書では sendMessage の説明にまとめて書いている
    @PostMapping(path = "/{username}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendWithImage(@PathVariable String username,
                                         @RequestParam(value = "body", required = false) String body,
                                         @RequestParam("image") MultipartFile file,
                                         HttpServletRequest httpRequest) throws IOException {
        String me = currentUser.require(httpRequest);
        User partner = partner(username, me);
        if (partner.getId().equals(me)) {
            throw ApiException.badRequest("自分にはメッセージを送れません");
        }
        String text = bodyOf(body, true);
        UploadedImage uploaded = UploadedImage.read(file);
        acquireSendSlot(me);
        // 画像を先に置き、メッセージを保存できなかったら画像を消す(投稿と同じ)
        String key = UUID.randomUUID().toString();
        imageStorage.put(key, uploaded.data(), uploaded.contentType());
        DirectMessage message = new DirectMessage(me, partner.getId(), text);
        message.attach(uploaded.toPostImage(key));
        try {
            DirectMessage saved = messages.save(message);
            realtime.send(partner.getId(), RealtimeHub.Event.DM, usernameOf(me));
            return MessageResponse.of(saved, me);
        } catch (RuntimeException e) {
            deleteImageQuietly(key);
            throw e;
        }
    }

    @Operation(operationId = "getMessageImage", summary = "DM の画像",
            description = "送った人と受け取った人だけが見られる。後から変わらないので、ブラウザに長くキャッシュさせる")
    @ApiResponse(responseCode = "200", description = "画像", content = {
            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))})
    @ApiErrors({400, 404})
    @GetMapping("/messages/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id, HttpServletRequest httpRequest) throws IOException {
        String me = currentUser.require(httpRequest);
        DirectMessage message = mine(id, me, false);
        PostImage image = Optional.ofNullable(message.getImage())
                .orElseThrow(() -> ApiException.notFound("画像が見つかりません: " + id));
        byte[] data = imageStorage.get(image.key());
        if (data == null) {
            throw ApiException.notFound("画像が見つかりません: " + id);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
    }

    @Operation(operationId = "deleteMessage", summary = "DM を取り消す",
            description = "自分が送ったものだけ消せる(相手の画面からも消える)。画像も消す。他人のもの・無いものは 404")
    @ApiErrors({400, 404})
    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        DirectMessage message = mine(id, me, true);
        PostImage image = message.getImage();
        messages.delete(message);
        messages.flush();
        realtime.send(message.getRecipientId(), RealtimeHub.Event.DM, usernameOf(me));
        if (image != null) {
            deleteImageQuietly(image.key());
        }
    }

    /**
     * 自分が関わるメッセージ。sentOnly なら自分が送ったものだけ。
     * ブロックしている・されている相手とのものは、無いものとして 404
     */
    private DirectMessage mine(Long id, String me, boolean sentOnly) {
        return messages.findById(id)
                .filter(m -> m.getSenderId().equals(me) || (!sentOnly && m.getRecipientId().equals(me)))
                .filter(m -> !visibility.blockedBetween(me, m.partnerOf(me)))
                .orElseThrow(() -> ApiException.notFound("メッセージが見つかりません: " + id));
    }

    /** 前後の空白を落とした本文。画像があれば空でもよい */
    private static String bodyOf(String value, boolean hasImage) {
        String body = value == null ? "" : value.strip();
        if (body.isEmpty() && !hasImage) {
            throw ApiException.badRequest("メッセージを入力してください");
        }
        if (body.length() > BODY_MAX_CHARS || TextLength.visible(body) > DirectMessage.BODY_MAX) {
            throw ApiException.badRequest("メッセージは" + DirectMessage.BODY_MAX + "文字以内で入力してください");
        }
        return body;
    }

    private String usernameOf(String userId) {
        return users.findById(userId).map(User::getUsername).orElse(null);
    }

    private void acquireSendSlot(String me) {
        if (!rateLimiter.tryAcquire("dm:" + me, SENDS_PER_WINDOW, SEND_WINDOW)) {
            throw ApiException.tooManyRequests("メッセージが多すぎます。1分ほど待ってからもう一度お試しください");
        }
    }

    private void deleteImageQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (Exception e) {
            log.warn("DM の画像を消せませんでした: {}", key, e);
        }
    }

    // ブロックしている・されている相手は、いないものとして扱う
    private User partner(String username, String me) {
        return (username.matches(User.USERNAME_PATTERN) ? users.findByUsername(username) : Optional.<User>empty())
                .filter(User::isEmailVerified)
                .filter(user -> !visibility.blockedBetween(me, user.getId()))
                .orElseThrow(() -> ApiException.notFound("ユーザーが見つかりません: @" + username));
    }
}
