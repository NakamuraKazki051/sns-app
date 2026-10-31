package com.snsapp.api.post;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.common.TextLength;
import com.snsapp.api.follow.FollowRepository;
import com.snsapp.api.like.PostLike;
import com.snsapp.api.like.PostLikeRepository;
import com.snsapp.api.post.PostDtos.CreateRequest;
import com.snsapp.api.post.PostDtos.LikeResponse;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostDtos.PostResponse;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PostController {

    /** 1ページの件数(タイムライン・ユーザーページ共通) */
    public static final int PAGE_SIZE = PostViews.PAGE_SIZE;

    // 連投対策: 1ユーザー1分間に10投稿まで(リプライも別枠で同じ回数)
    public static final int POSTS_PER_WINDOW = 10;
    public static final Duration POST_WINDOW = Duration.ofMinutes(1);

    // 見た目で140字でも、結合文字を大量に重ねた文字列は巨大になりうる。数える前に生の長さでも止める
    static final int BODY_MAX_CHARS = 2000;

    private final PostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final PostViews postViews;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;

    public PostController(PostRepository postRepository, PostLikeRepository likeRepository,
                          FollowRepository followRepository, UserRepository userRepository, PostViews postViews,
                          CurrentUser currentUser, RateLimiter rateLimiter) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.postViews = postViews;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    /** 全体タイムライン。before(投稿 ID)を渡すと、それより古いものから */
    @GetMapping("/timeline")
    public PageResponse timeline(@RequestParam(required = false) Long before, HttpServletRequest httpRequest) {
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
    @GetMapping("/timeline/home")
    public PageResponse home(@RequestParam(required = false) String before, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        List<String> authorIds = new ArrayList<>(followRepository.findFolloweeIds(userId));
        authorIds.add(userId);
        return postViews.feedOf(authorIds, before, userId);
    }

    @GetMapping("/posts/{id}")
    public PostResponse show(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return postViews.one(findPost(id), userId);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@RequestBody CreateRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (!rateLimiter.tryAcquire("post:" + userId, POSTS_PER_WINDOW, POST_WINDOW)) {
            throw ApiException.tooManyRequests("投稿が多すぎます。1分ほど待ってからもう一度お試しください");
        }
        String body = bodyValue(request.body(), "投稿");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
        return postViews.one(postRepository.save(new Post(user, body)), userId);
    }

    // 他人の投稿は 403 ではなく 404 にする(消せる投稿が存在しない、という扱い)。リプライといいねは DB が一緒に消す
    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (postRepository.deleteOwn(id, userId) == 0) {
            throw ApiException.notFound("投稿が見つかりません: " + id);
        }
    }

    /** いいねする。もういいね済みならそのまま(何度押しても1回分) */
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
