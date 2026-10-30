package com.snsapp.api.user;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.follow.Follow;
import com.snsapp.api.follow.FollowRepository;
import com.snsapp.api.post.PostController;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostRepository;
import com.snsapp.api.post.PostViews;
import com.snsapp.api.user.UserDtos.FollowResponse;
import com.snsapp.api.user.UserDtos.ProfileResponse;
import com.snsapp.api.user.UserDtos.UpdateRequest;
import com.snsapp.api.user.UserDtos.UserPage;
import com.snsapp.api.user.UserDtos.UserResponse;
import com.snsapp.api.user.UserDtos.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.function.Function;

@RestController
@RequestMapping("/api/users")
public class UserController {

    /** 検索・フォロー一覧の1ページの件数 */
    public static final int PAGE_SIZE = 20;

    // 検索語はユーザー名(15字)・表示名(50字)の一部なので、それより長いものは探しても見つからない
    static final int QUERY_MAX = 50;

    private final UserRepository userRepository;
    private final UserIconRepository userIconRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final PostViews postViews;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserController(UserRepository userRepository, UserIconRepository userIconRepository,
                          PostRepository postRepository, FollowRepository followRepository, PostViews postViews,
                          PasswordEncoder passwordEncoder, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.userIconRepository = userIconRepository;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.postViews = postViews;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    /** ユーザー検索: ユーザー名か表示名の一部。ユーザー名順に20件ずつ。after には前のページの nextCursor を渡す */
    @GetMapping
    public UserPage search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String after,
                           HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        String query = q.strip();
        if (query.isEmpty()) {
            return new UserPage(List.of(), null);
        }
        if (query.length() > QUERY_MAX) {
            throw ApiException.badRequest("検索語は" + QUERY_MAX + "文字以内で入力してください");
        }
        // % や _ をそのまま探せるよう、LIKE の特殊文字をエスケープする
        String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<User> fetched = userRepository.search(pattern, after, Limit.of(PAGE_SIZE + 1));
        return toUserPage(fetched, Function.identity(), User::getUsername);
    }

    /** ユーザーページ: 自己紹介、投稿数、フォロー数・フォロワー数、自分がフォローしているか */
    @GetMapping("/{username}")
    public ProfileResponse show(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username);
        return new ProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getIconUpdatedAt(),
                user.getBio(), postRepository.countByUserId(user.getId()),
                followRepository.countByFollowerId(user.getId()), followRepository.countByFolloweeId(user.getId()),
                followRepository.existsByFollowerIdAndFolloweeId(viewerId, user.getId()), user.getCreatedAt());
    }

    /** その人の投稿一覧。before(投稿 ID)を渡すと、それより古いものから */
    @GetMapping("/{username}/posts")
    public PageResponse posts(@PathVariable String username, @RequestParam(required = false) Long before,
                              HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username);
        Limit limit = Limit.of(PostController.PAGE_SIZE + 1);
        return postViews.page(before == null
                ? postRepository.findByUserIdOrderByIdDesc(user.getId(), limit)
                : postRepository.findByUserIdAndIdLessThanOrderByIdDesc(user.getId(), before, limit), viewerId);
    }

    /** フォローする。もうフォロー済みならそのまま(何度押しても1回分) */
    @PostMapping("/{username}/follow")
    public FollowResponse follow(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username);
        if (user.getId().equals(viewerId)) {
            throw ApiException.badRequest("自分自身はフォローできません");
        }
        if (!followRepository.existsByFollowerIdAndFolloweeId(viewerId, user.getId())) {
            try {
                followRepository.saveAndFlush(new Follow(userRepository.getReferenceById(viewerId), user));
            } catch (DataIntegrityViolationException e) {
                // 上の確認と保存の間に同じフォローが入った(連打)。一意制約で弾かれたので、フォロー済みとして扱う
            }
        }
        return new FollowResponse(true, followRepository.countByFolloweeId(user.getId()));
    }

    /** フォローを解除する。フォローしていなければそのまま */
    @DeleteMapping("/{username}/follow")
    public FollowResponse unfollow(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User user = findUser(username);
        followRepository.deleteByFollowerIdAndFolloweeId(viewerId, user.getId());
        return new FollowResponse(false, followRepository.countByFolloweeId(user.getId()));
    }

    /** フォロー中の一覧(新しくフォローした順)。before には前のページの nextCursor を渡す */
    @GetMapping("/{username}/following")
    public UserPage following(@PathVariable String username, @RequestParam(required = false) Long before,
                              HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        User user = findUser(username);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Follow> fetched = before == null
                ? followRepository.findByFollowerIdOrderByIdDesc(user.getId(), limit)
                : followRepository.findByFollowerIdAndIdLessThanOrderByIdDesc(user.getId(), before, limit);
        return toUserPage(fetched, Follow::getFollowee, f -> f.getId().toString());
    }

    /** フォロワーの一覧(新しくフォローされた順)。before には前のページの nextCursor を渡す */
    @GetMapping("/{username}/followers")
    public UserPage followers(@PathVariable String username, @RequestParam(required = false) Long before,
                              HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        User user = findUser(username);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Follow> fetched = before == null
                ? followRepository.findByFolloweeIdOrderByIdDesc(user.getId(), limit)
                : followRepository.findByFolloweeIdAndIdLessThanOrderByIdDesc(user.getId(), before, limit);
        return toUserPage(fetched, Follow::getFollower, f -> f.getId().toString());
    }

    /** アイコン画像。URL に ?v=<更新時刻> が付くので、ブラウザには長くキャッシュさせる */
    @GetMapping("/{username}/icon")
    public ResponseEntity<byte[]> icon(@PathVariable String username, HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        UserIcon icon = userIconRepository.findById(findUser(username).getId())
                .orElseThrow(() -> ApiException.notFound("アイコンが設定されていません: @" + username));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(icon.getData());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("現在のパスワードが正しくありません");
        }

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw ApiException.conflict("このメールアドレスは既に登録されています: " + email);
            }
            user.setEmail(email);
        }

        boolean passwordChanged = request.newPassword() != null && !request.newPassword().isBlank();
        if (passwordChanged) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("このメールアドレスは既に登録されています: " + user.getEmail());
        }
        if (passwordChanged) {
            // 他の端末のセッションはパスワード不一致で切れる。変更した本人のセッションだけ新しいパスワードで維持する
            currentUser.signIn(httpRequest, saved);
        }
        return UserResponse.from(saved);
    }

    // ユーザー名は大文字・小文字を区別しない(DB の照合順序による)
    private User findUser(String username) {
        return (username.matches(User.USERNAME_PATTERN) ? userRepository.findByUsername(username) : Optional.<User>empty())
                .orElseThrow(() -> ApiException.notFound("ユーザーが見つかりません: @" + username));
    }

    /** 1件多く読んだ結果から、1ページ分と次の目印を作る */
    private static <T> UserPage toUserPage(List<T> fetched, Function<T, User> toUser, Function<T, String> toCursor) {
        List<T> page = fetched.stream().limit(PAGE_SIZE).toList();
        String next = fetched.size() > PAGE_SIZE ? toCursor.apply(page.getLast()) : null;
        return new UserPage(page.stream().map(toUser).map(UserSummary::from).toList(), next);
    }
}
