package com.snsapp.api.post;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.ImageFiles;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.common.TextLength;
import com.snsapp.api.follow.FollowRepository;
import com.snsapp.api.like.PostLike;
import com.snsapp.api.like.PostLikeRepository;
import com.snsapp.api.post.PostDtos.CreateRequest;
import com.snsapp.api.post.PostDtos.EditRequest;
import com.snsapp.api.post.PostDtos.ImagePostForm;
import com.snsapp.api.post.PostDtos.LikeResponse;
import com.snsapp.api.post.PostDtos.NewPostsResponse;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostDtos.PostResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = OpenApiConfig.TAG_POST)
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

    /** 1ページの件数(タイムライン・ユーザーページ共通) */
    public static final int PAGE_SIZE = PostViews.PAGE_SIZE;

    // 連投対策: 1ユーザー1分間に10投稿まで(リプライも別枠で同じ回数)
    public static final int POSTS_PER_WINDOW = 10;
    public static final Duration POST_WINDOW = Duration.ofMinutes(1);

    // 見た目で140字でも、結合文字を大量に重ねた文字列は巨大になりうる。数える前に生の長さでも止める
    static final int BODY_MAX_CHARS = 2000;

    /** 新着の数はここまでしか数えない(画面は「99+」と出す) */
    public static final int NEW_POSTS_MAX = 100;
    /** 新着のお知らせに並べるアイコンの数 */
    static final int NEW_POSTS_USERS = 3;

    private final PostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final PostViews postViews;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;
    private final ImageStorage imageStorage;

    public PostController(PostRepository postRepository, PostLikeRepository likeRepository,
                          FollowRepository followRepository, UserRepository userRepository, PostViews postViews,
                          CurrentUser currentUser, RateLimiter rateLimiter, ImageStorage imageStorage) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.postViews = postViews;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.imageStorage = imageStorage;
    }

    /** 全体タイムライン。before(投稿 ID)を渡すと、それより古いものから */
    @Operation(operationId = "getTimeline", summary = "全体タイムライン",
            description = "みんなの投稿を新しい順に20件。リポストは混ざらない")
    @ApiErrors(400)
    @GetMapping("/timeline")
    public PageResponse timeline(
            @Parameter(description = "前のページの nextCursor(投稿 ID)。これより古い投稿から読む")
            @RequestParam(required = false) Long before, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        return postViews.page(before == null
                ? postRepository.findByOrderByIdDesc(limit)
                : postRepository.findByIdLessThanOrderByIdDesc(before, limit), userId);
    }

    /**
     * ホームタイムライン: 自分とフォロー中の人の投稿と、その人たちのリポスト。
     * before には前のページの nextCursor を渡す(投稿とリポストのどこまで出したかが入っている)
     */
    @Operation(operationId = "getHomeTimeline", summary = "ホームタイムライン",
            description = "自分とフォロー中の人の投稿とリポストを新しい順に20件")
    @ApiErrors(400)
    @GetMapping("/timeline/home")
    public PageResponse home(
            @Parameter(description = "前のページの nextCursor。形式は 投稿ID:リポストID(どちらかが空のこともある)")
            @RequestParam(required = false) String before, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return postViews.feedOf(authorsOfHome(userId), before, userId);
    }

    /**
     * 全体タイムラインの新着: after(画面に出ている一番新しい投稿の ID)より新しい、他の人の投稿の数。
     * 画面が30秒ごとに聞きに来て、あれば「新しい投稿 N件」を出す
     */
    @Operation(operationId = "countNewPosts", summary = "全体タイムラインの新着",
            description = "after より新しい、他の人の投稿の数(100まで)と、投稿者3人まで")
    @ApiErrors(400)
    @GetMapping("/timeline/new")
    public NewPostsResponse newPosts(
            @Parameter(description = "画面に出ている一番新しい投稿の ID") @RequestParam long after,
            HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return newPostsOf(postRepository.findByIdGreaterThanAndUserIdNotOrderByIdDesc(
                after, userId, Limit.of(NEW_POSTS_MAX)));
    }

    /** ホームタイムラインの新着: フォロー中の人の新しい投稿(自分の投稿とリポストは数えない) */
    @Operation(operationId = "countNewHomePosts", summary = "ホームタイムラインの新着",
            description = "after より新しい、フォロー中の人の投稿の数(100まで)と、投稿者3人まで。自分の投稿とリポストは数えない")
    @ApiErrors(400)
    @GetMapping("/timeline/home/new")
    public NewPostsResponse newHomePosts(
            @Parameter(description = "画面に出ている一番新しい投稿の ID") @RequestParam long after,
            HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        List<String> followees = followRepository.findFolloweeIds(userId);
        if (followees.isEmpty()) {
            return new NewPostsResponse(0, List.of());
        }
        return newPostsOf(postRepository.findByUserIdInAndIdGreaterThanOrderByIdDesc(
                followees, after, Limit.of(NEW_POSTS_MAX)));
    }

    private static NewPostsResponse newPostsOf(List<Post> posts) {
        List<PublicUser> users = posts.stream()
                .map(post -> PublicUser.from(post.getUser()))
                .distinct()
                .limit(NEW_POSTS_USERS)
                .toList();
        return new NewPostsResponse(posts.size(), users);
    }

    private List<String> authorsOfHome(String userId) {
        List<String> authorIds = new ArrayList<>(followRepository.findFolloweeIds(userId));
        authorIds.add(userId);
        return authorIds;
    }

    @Operation(operationId = "getPost", summary = "投稿を1件読む")
    @ApiErrors({400, 404})
    @GetMapping("/posts/{id}")
    public PostResponse show(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return postViews.one(findPost(id), userId);
    }

    @Operation(operationId = "createPost", summary = "投稿する",
            description = """
                    本文だけなら JSON、画像を付けるなら multipart/form-data で送る。
                    本文は140字まで(絵文字も1字と数える)。画像は JPEG か PNG を1枚、5MBまで、縦・横それぞれの上限あり。
                    画像があれば本文は空でもよい。投稿は1人1分に10回まで""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateRequest.class)),
                    @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = ImagePostForm.class))}))
    @ApiErrors({400, 413, 429})
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@RequestBody CreateRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        String body = bodyValue(request.body(), "投稿");
        // 入力の確認が済んでから回数に数える(空の投稿で弾かれた分は数えない)
        acquirePostSlot(userId);
        return postViews.one(postRepository.save(new Post(findUser(userId), body)), userId);
    }

    /**
     * 画像付きの投稿(multipart/form-data)。image に JPEG か PNG を1枚(5MBまで)、body に本文。
     * 画像があれば本文は空でもよい
     */
    @Hidden // 仕様書では createPost に2つの形式としてまとめて書いている
    @PostMapping(path = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createWithImage(@RequestParam(value = "body", required = false) String body,
                                        @RequestParam("image") MultipartFile file,
                                        HttpServletRequest httpRequest) throws IOException {
        String userId = currentUser.require(httpRequest);
        String text = body == null || body.isBlank() ? "" : bodyValue(body, "投稿");
        if (file.isEmpty()) {
            throw ApiException.badRequest("画像を選んでください");
        }
        if (file.getSize() > PostImage.MAX_BYTES) {
            throw ApiException.payloadTooLarge("画像は5MBまでです");
        }
        byte[] data = file.getBytes();
        String contentType = ImageFiles.detectType(data);
        if (contentType == null) {
            throw ApiException.badRequest("画像は JPEG か PNG にしてください");
        }
        ImageFiles.Size size = ImageFiles.readSize(data);
        if (size == null) {
            throw ApiException.badRequest("画像を読み込めませんでした。別の画像でお試しください");
        }
        if (size.width() > PostImage.MAX_SIDE || size.height() > PostImage.MAX_SIDE) {
            throw ApiException.badRequest("画像が大きすぎます(縦・横それぞれ" + PostImage.MAX_SIDE + "ピクセルまで)");
        }
        // 入力の確認がすべて済んでから回数に数える(形式違いで弾かれた分は数えない)
        acquirePostSlot(userId);
        User user = findUser(userId);

        // 画像を先に置き、投稿を保存できなかったら画像を消す(画像の無い投稿が残らないように)
        String key = UUID.randomUUID().toString();
        imageStorage.put(key, data, contentType);
        Post post;
        try {
            post = postRepository.save(new Post(user, text,
                    new PostImage(key, contentType, size.width(), size.height())));
        } catch (RuntimeException e) {
            deleteImageQuietly(key);
            throw e;
        }
        // 保存できたあとは画像を消さない(応答を作るところで失敗しても、投稿は残っている)
        return postViews.one(post, userId);
    }

    /** 投稿の画像。投稿の画像は後から変わらないので、ブラウザには長くキャッシュさせる */
    @Operation(operationId = "getPostImage", summary = "投稿の画像")
    @ApiResponse(responseCode = "200", description = "画像", content = {
            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))})
    @ApiErrors({400, 404})
    @GetMapping("/posts/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id, HttpServletRequest httpRequest) throws IOException {
        currentUser.require(httpRequest);
        PostImage image = postRepository.findById(id).map(Post::getImage)
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

    /** 自分の投稿の本文を直す(F-19)。他人の投稿は消すときと同じく 404 */
    @Operation(operationId = "editPost", summary = "投稿の本文を直す",
            description = "自分の投稿だけ直せる。画像はそのまま。本文は140字まで、画像のある投稿なら空でもよい。"
                    + "直すと editedAt が入り、画面に「編集済み」と出る。投稿と合わせて1人1分に10回まで")
    @ApiErrors({400, 404, 429})
    @PatchMapping("/posts/{id}")
    @Transactional
    public PostResponse edit(@PathVariable Long id, @RequestBody EditRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Post post = postRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("投稿が見つかりません: " + id));
        String body = post.getImage() != null && (request.body() == null || request.body().isBlank())
                ? ""
                : bodyValue(request.body(), "投稿");
        if (body.equals(post.getBody())) {
            // 何も変わらないなら「編集済み」にしない
            return postViews.one(post, userId);
        }
        acquirePostSlot(userId);
        post.edit(body, Instant.now());
        return postViews.one(postRepository.saveAndFlush(post), userId);
    }

    // 他人の投稿は 403 ではなく 404 にする(消せる投稿が存在しない、という扱い)。リプライといいねは DB が一緒に消す
    @Operation(operationId = "deletePost", summary = "投稿を消す",
            description = "自分の投稿だけ消せる。リプライ・いいね・リポスト・画像も一緒に消える")
    @ApiErrors({400, 404})
    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        PostImage image = postRepository.findByIdAndUserId(id, userId).map(Post::getImage).orElse(null);
        if (postRepository.deleteOwn(id, userId) == 0) {
            throw ApiException.notFound("投稿が見つかりません: " + id);
        }
        // 投稿を消したあとで画像を消す(画像だけ先に消えて、表示が壊れた投稿が残らないように)
        if (image != null) {
            deleteImageQuietly(image.key());
        }
    }

    /** いいねする。もういいね済みならそのまま(何度押しても1回分) */
    @Operation(operationId = "likePost", summary = "いいねする",
            description = "いいね済みなら何も変わらない")
    @ApiErrors({400, 404})
    @PostMapping("/posts/{id}/like")
    public LikeResponse like(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Post post = findPost(id);
        if (!likeRepository.existsByUserIdAndPostId(userId, id)) {
            try {
                likeRepository.saveAndFlush(new PostLike(userRepository.getReferenceById(userId), post));
            } catch (DataIntegrityViolationException e) {
                // 上の確認と保存の間に同じいいねが入った(連打)。一意制約で弾かれたので、いいね済みとして扱う
            }
        }
        return new LikeResponse(true, likeRepository.countByPostId(id));
    }

    /** いいねを取り消す。いいねしていなければそのまま */
    @Operation(operationId = "unlikePost", summary = "いいねを取り消す",
            description = "いいねしていなければ何も変わらない")
    @ApiErrors({400, 404})
    @DeleteMapping("/posts/{id}/like")
    public LikeResponse unlike(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        findPost(id);
        likeRepository.deleteByUserIdAndPostId(userId, id);
        return new LikeResponse(false, likeRepository.countByPostId(id));
    }

    public Post findPost(Long id) {
        return postRepository.findWithUserById(id)
                .orElseThrow(() -> ApiException.notFound("投稿が見つかりません: " + id));
    }

    private void acquirePostSlot(String userId) {
        if (!rateLimiter.tryAcquire("post:" + userId, POSTS_PER_WINDOW, POST_WINDOW)) {
            throw ApiException.tooManyRequests("投稿が多すぎます。1分ほど待ってからもう一度お試しください");
        }
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
    }

    // 画像が消せなくても投稿の操作は失敗にしない(置き場所にゴミが残るだけ)。ログには残す
    private void deleteImageQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (Exception e) {
            log.warn("投稿の画像を消せませんでした: {}", key, e);
        }
    }

    /** 改行は残し、前後の空白だけ取る。空白だけ・140字超えは受け付けない(投稿とリプライ共通) */
    public static String bodyValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(label + "の本文を入力してください");
        }
        String body = value.strip();
        if (body.length() > BODY_MAX_CHARS || TextLength.visible(body) > Post.BODY_MAX) {
            throw ApiException.badRequest(label + "は" + Post.BODY_MAX + "文字以内で入力してください");
        }
        return body;
    }
}
