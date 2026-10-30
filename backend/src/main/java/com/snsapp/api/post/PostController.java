package com.snsapp.api.post;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.post.PostDtos.CreateRequest;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostDtos.PostResponse;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
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

import java.text.BreakIterator;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PostController {

    /** 1ページの件数(タイムライン・ユーザーページ共通) */
    public static final int PAGE_SIZE = 20;

    // 連投対策: 1ユーザー1分間に10投稿まで
    public static final int POSTS_PER_WINDOW = 10;
    public static final Duration POST_WINDOW = Duration.ofMinutes(1);

    // 見た目で140字でも、結合文字を大量に重ねた文字列は巨大になりうる。数える前に生の長さでも止める
    static final int BODY_MAX_CHARS = 2000;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;

    public PostController(PostRepository postRepository, UserRepository userRepository, CurrentUser currentUser,
                          RateLimiter rateLimiter) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    /** 全体タイムライン。before(投稿 ID)を渡すと、それより古いものから */
    @GetMapping("/timeline")
    public PageResponse timeline(@RequestParam(required = false) Long before, HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        return toPage(before == null
                ? postRepository.findByOrderByIdDesc(limit)
                : postRepository.findByIdLessThanOrderByIdDesc(before, limit));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@RequestBody CreateRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (!rateLimiter.tryAcquire("post:" + userId, POSTS_PER_WINDOW, POST_WINDOW)) {
            throw ApiException.tooManyRequests("投稿が多すぎます。1分ほど待ってからもう一度お試しください");
        }
        String body = bodyValue(request.body());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
        return PostResponse.from(postRepository.save(new Post(user, body)));
    }

    // 他人の投稿は 403 ではなく 404 にする(消せる投稿が存在しない、という扱い)
    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (postRepository.deleteOwn(id, userId) == 0) {
            throw ApiException.notFound("投稿が見つかりません: " + id);
        }
    }

    /** 1件多く読んだ結果から、1ページ分と「続きがあるか」を作る */
    public static PageResponse toPage(List<Post> fetched) {
        List<PostResponse> posts = fetched.stream().limit(PAGE_SIZE).map(PostResponse::from).toList();
        return new PageResponse(posts, fetched.size() > PAGE_SIZE);
    }

    /** 140字は「見た目の1文字」で数える。絵文字や結合文字も1字(画面の Intl.Segmenter と同じ数え方) */
    static int visibleLength(String text) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        int count = 0;
        while (graphemes.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }

    // 改行は残し、前後の空白だけ取る。空白だけの投稿は受け付けない
    private static String bodyValue(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("投稿の本文を入力してください");
        }
        String body = value.strip();
        if (body.length() > BODY_MAX_CHARS || visibleLength(body) > Post.BODY_MAX) {
            throw ApiException.badRequest("投稿は" + Post.BODY_MAX + "文字以内で入力してください");
        }
        return body;
    }
}
