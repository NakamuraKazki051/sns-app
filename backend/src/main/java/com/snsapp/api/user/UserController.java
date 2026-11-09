package com.snsapp.api.user;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.BlockRepository;
import com.snsapp.api.block.MuteRepository;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.notification.Notification;
import com.snsapp.api.notification.NotificationService;
import com.snsapp.api.auth.EmailVerificationService;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.follow.Follow;
import com.snsapp.api.follow.FollowRepository;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostRepository;
import com.snsapp.api.post.PostViews;
import com.snsapp.api.user.UserDtos.FollowResponse;
import com.snsapp.api.user.UserDtos.ProfileResponse;
import com.snsapp.api.user.UserDtos.UpdateRequest;
import com.snsapp.api.user.UserDtos.UserPage;
import com.snsapp.api.user.UserDtos.UserResponse;
import com.snsapp.api.user.UserDtos.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@RestController
@RequestMapping("/api/users")
@Tag(name = OpenApiConfig.TAG_USER)
public class UserController {

    /** 検索・フォロー一覧の1ページの件数 */
    public static final int PAGE_SIZE = 20;

    // 検索語はユーザー名(15字)・表示名(50字)の一部なので、それより長いものは探しても見つからない
    static final int QUERY_MAX = 50;

    // アイコンはキャッシュするので、見る人によって変えない(ブロックしていても一覧のアイコンは出る)
    private static final String NO_VIEWER = "-";

    private static final int EMAIL_CHANGES = 3;
    private static final Duration EMAIL_CHANGE_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final UserIconRepository userIconRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final PostViews postViews;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final EmailVerificationService emailVerification;
    private final RateLimiter rateLimiter;
    private final Visibility visibility;
    private final BlockRepository blockRepository;
    private final MuteRepository muteRepository;
    private final NotificationService notifications;

    public UserController(UserRepository userRepository, UserIconRepository userIconRepository,
                          PostRepository postRepository, FollowRepository followRepository, PostViews postViews,
                          PasswordEncoder passwordEncoder, CurrentUser currentUser,
                          EmailVerificationService emailVerification, RateLimiter rateLimiter,
                          Visibility visibility, BlockRepository blockRepository, MuteRepository muteRepository,
                          NotificationService notifications) {
        this.notifications = notifications;
        this.visibility = visibility;
        this.blockRepository = blockRepository;
        this.muteRepository = muteRepository;
        this.userRepository = userRepository;
        this.userIconRepository = userIconRepository;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.postViews = postViews;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.emailVerification = emailVerification;
        this.rateLimiter = rateLimiter;
    }

    /** ユーザー検索: ユーザー名か表示名の一部。ユーザー名順に20件ずつ。after には前のページの nextCursor を渡す */
    @Operation(operationId = "searchUsers", summary = "ユーザー検索",
            description = "ユーザー名か表示名の一部で探す。ユーザー名順に20件")
    @ApiErrors(400)
    @GetMapping
    public UserPage search(
            @Parameter(description = "検索語(50文字まで)。空なら結果も空") @RequestParam(defaultValue = "") String q,
            @Parameter(description = "前のページの nextCursor") @RequestParam(defaultValue = "") String after,
                           HttpServletRequest httpRequest) {
        String viewer = currentUser.require(httpRequest);
        String query = q.strip();
        if (query.isEmpty()) {
            return new UserPage(List.of(), null);
        }
        if (query.length() > QUERY_MAX) {
            throw ApiException.badRequest("検索語は" + QUERY_MAX + "文字以内で入力してください");
        }
        // % や _ をそのまま探せるよう、LIKE の特殊文字をエスケープする
        String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        // ブロックしている・されている人は出さない(F-20)
        List<User> fetched = userRepository.search(pattern, after, visibility.blockRelated(viewer), Limit.of(PAGE_SIZE + 1));
        return toUserPage(fetched, Function.identity(), User::getUsername);
    }

    /** ユーザーページ: 自己紹介、投稿数、フォロー数・フォロワー数、自分がフォローしているか */
    @Operation(operationId = "getProfile", summary = "ユーザーページの情報",
            description = "自己紹介、投稿数、フォロー数、フォロワー数、自分がフォローしているか")
    @ApiErrors(404)
    @GetMapping("/{username}")
    public ProfileResponse show(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username, viewerId);
        return new ProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getIconUpdatedAt(),
                user.getBio(), postRepository.countByUserId(user.getId()),
                followRepository.countByFollowerId(user.getId()), followRepository.countByFolloweeId(user.getId()),
                followRepository.existsByFollowerIdAndFolloweeId(viewerId, user.getId()), user.getCreatedAt(),
                blockRepository.existsByBlockerIdAndBlockedId(viewerId, user.getId()),
                muteRepository.existsByMuterIdAndMutedId(viewerId, user.getId()));
    }

    /** その人の投稿とリポストの一覧。before には前のページの nextCursor を渡す */
    @Operation(operationId = "listUserPosts", summary = "その人の投稿とリポスト",
            description = "新しい順に20件")
    @ApiErrors({400, 404})
    @GetMapping("/{username}/posts")
    public PageResponse posts(@PathVariable String username,
                              @Parameter(description = "前のページの nextCursor。形式は 投稿ID:リポストID(どちらかが空のこともある)")
                              @RequestParam(required = false) String before,
                              HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username, viewerId);
        // 自分がブロックしている人のページは開けるが、投稿は出さない(F-20)
        if (blockRepository.existsByBlockerIdAndBlockedId(viewerId, user.getId())) {
            return new PageResponse(List.of(), false, null);
        }
        return postViews.feedOf(List.of(user.getId()), before, viewerId);
    }

    /** フォローする。もうフォロー済みならそのまま(何度押しても1回分) */
    @Operation(operationId = "follow", summary = "フォローする",
            description = "フォロー済みなら何も変わらない。自分はフォローできない(400)")
    @ApiErrors({400, 404})
    @PostMapping("/{username}/follow")
    public FollowResponse follow(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username, viewerId);
        if (user.getId().equals(viewerId)) {
            throw ApiException.badRequest("自分自身はフォローできません");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(viewerId, user.getId())) {
            throw ApiException.badRequest("ブロック中のユーザーはフォローできません。先にブロックを解除してください");
        }
        if (!followRepository.existsByFollowerIdAndFolloweeId(viewerId, user.getId())) {
            try {
                followRepository.saveAndFlush(new Follow(userRepository.getReferenceById(viewerId), user));
                notifications.followed(viewerId, user.getId());
            } catch (DataIntegrityViolationException e) {
                // 上の確認と保存の間に同じフォローが入った(連打)。一意制約で弾かれたので、フォロー済みとして扱う
            }
        }
        return new FollowResponse(true, followRepository.countByFolloweeId(user.getId()));
    }

    /** フォローを解除する。フォローしていなければそのまま */
    @Operation(operationId = "unfollow", summary = "フォローを解除する",
            description = "フォローしていなければ何も変わらない")
    @ApiErrors(404)
    @DeleteMapping("/{username}/follow")
    public FollowResponse unfollow(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username, viewerId);
        followRepository.deleteByFollowerIdAndFolloweeId(viewerId, user.getId());
        notifications.retract(Notification.Type.FOLLOW, viewerId, user.getId(), null);
        return new FollowResponse(false, followRepository.countByFolloweeId(user.getId()));
    }

    /** フォロー中の一覧(新しくフォローした順)。before には前のページの nextCursor を渡す */
    @Operation(operationId = "listFollowing", summary = "フォロー中の一覧",
            description = "新しくフォローした順に20件")
    @ApiErrors({400, 404})
    @GetMapping("/{username}/following")
    public UserPage following(@PathVariable String username,
                              @Parameter(description = "前のページの nextCursor") @RequestParam(required = false) Long before,
                              HttpServletRequest httpRequest) {
        String viewer = currentUser.require(httpRequest);
        User user = findUser(username, viewer);
        Set<String> hidden = visibility.blockRelated(viewer);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Follow> fetched = before == null
                ? followRepository.findByFollowerIdAndFolloweeIdNotInOrderByIdDesc(user.getId(), hidden, limit)
                : followRepository.findByFollowerIdAndFolloweeIdNotInAndIdLessThanOrderByIdDesc(user.getId(), hidden, before, limit);
        return toUserPage(fetched, Follow::getFollowee, f -> f.getId().toString());
    }

    /** フォロワーの一覧(新しくフォローされた順)。before には前のページの nextCursor を渡す */
    @Operation(operationId = "listFollowers", summary = "フォロワーの一覧",
            description = "新しくフォローされた順に20件")
    @ApiErrors({400, 404})
    @GetMapping("/{username}/followers")
    public UserPage followers(@PathVariable String username,
                              @Parameter(description = "前のページの nextCursor") @RequestParam(required = false) Long before,
                              HttpServletRequest httpRequest) {
        String viewer = currentUser.require(httpRequest);
        User user = findUser(username, viewer);
        Set<String> hidden = visibility.blockRelated(viewer);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Follow> fetched = before == null
                ? followRepository.findByFolloweeIdAndFollowerIdNotInOrderByIdDesc(user.getId(), hidden, limit)
                : followRepository.findByFolloweeIdAndFollowerIdNotInAndIdLessThanOrderByIdDesc(user.getId(), hidden, before, limit);
        return toUserPage(fetched, Follow::getFollower, f -> f.getId().toString());
    }

    /** アイコン画像。URL に ?v=<更新時刻> が付くので、ブラウザには長くキャッシュさせる */
    @Operation(operationId = "getIcon", summary = "アイコン画像")
    @ApiResponse(responseCode = "200", description = "画像", content = {
            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))})
    @ApiErrors(404)
    @GetMapping("/{username}/icon")
    public ResponseEntity<byte[]> icon(@PathVariable String username, HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        UserIcon icon = userIconRepository.findById(findUser(username, NO_VIEWER).getId())
                .orElseThrow(() -> ApiException.notFound("アイコンが設定されていません: @" + username));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(icon.getData());
    }

    @Operation(operationId = "updateAccount", summary = "メールアドレスとパスワードを変える",
            description = "現在のパスワードが要る。パスワードを変えると、他の端末はログアウトされる(次の操作で 401)。"
                    + "メールアドレスは、新しいアドレスに届く確認メールのリンクを開くまで変わらない(1時間に3回まで)")
    @ApiErrors({400, 409, 429, 503})
    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateRequest request, HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        String userId = currentUser.require(httpRequest);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("現在のパスワードが正しくありません");
        }

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(user.getEmail())) {
                if (userRepository.existsByEmail(email)) {
                    throw ApiException.conflict("このメールアドレスは既に登録されています: " + email);
                }
                if (emailVerification.isRequired()) {
                    // 他人のアドレスに確認メールを送り続けられないように数える
                    if (!rateLimiter.tryAcquire("email-change:" + userId, EMAIL_CHANGES, EMAIL_CHANGE_WINDOW)) {
                        throw ApiException.tooManyRequests("メールアドレスの変更の回数が多すぎます。1時間ほど待ってからもう一度お試しください");
                    }
                    emailVerification.send(user, email);
                } else {
                    user.setEmail(email);
                }
            }
        }

        boolean passwordChanged = request.newPassword() != null && !request.newPassword().isBlank();
        if (passwordChanged) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            // 古いパスワードで入っていた端末(パスワードを盗んだ人かもしれない)を、すべて締め出す
            user.revokeAllTokens();
        }

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("このメールアドレスは既に登録されています: " + user.getEmail());
        }
        if (passwordChanged) {
            // 世代が進んで今の JWT も使えなくなったので、変更した本人にだけ新しい世代の JWT を渡す
            currentUser.signIn(httpResponse, saved);
        }
        return UserResponse.from(saved);
    }

    // ユーザー名は大文字・小文字を区別しない(DB の照合順序による)。
    // メールの確認が済んでいない登録はいないものとして扱う。フォローされると、期限切れの登録を消せなくなる
    // 自分をブロックしている人も、いないものとして扱う(F-20)。自分がブロックしている人は、解除できるよう見える
    private User findUser(String username, String viewerId) {
        return (username.matches(User.USERNAME_PATTERN) ? userRepository.findByUsername(username) : Optional.<User>empty())
                .filter(User::isEmailVerified)
                .filter(user -> !blockRepository.existsByBlockerIdAndBlockedId(user.getId(), viewerId))
                .orElseThrow(() -> ApiException.notFound("ユーザーが見つかりません: @" + username));
    }

    /** 1件多く読んだ結果から、1ページ分と次の目印を作る */
    private static <T> UserPage toUserPage(List<T> fetched, Function<T, User> toUser, Function<T, String> toCursor) {
        List<T> page = fetched.stream().limit(PAGE_SIZE).toList();
        String next = fetched.size() > PAGE_SIZE ? toCursor.apply(page.getLast()) : null;
        return new UserPage(page.stream().map(toUser).map(UserSummary::from).toList(), next);
    }
}
