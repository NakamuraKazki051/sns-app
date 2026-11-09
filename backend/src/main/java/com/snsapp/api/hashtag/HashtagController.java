package com.snsapp.api.hashtag;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostRepository;
import com.snsapp.api.post.PostViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** ハッシュタグの投稿一覧とトレンド(F-22) */
@RestController
@RequestMapping("/api")
@Tag(name = OpenApiConfig.TAG_POST)
public class HashtagController {

    /** トレンドに出すタグの数と、数える期間 */
    static final int TRENDS_MAX = 10;
    static final Duration TRENDS_WINDOW = Duration.ofHours(24);

    public record Trend(String tag, long postsCount) {
    }

    private final PostRepository postRepository;
    private final PostHashtagRepository hashtags;
    private final PostViews postViews;
    private final CurrentUser currentUser;
    private final Visibility visibility;

    public HashtagController(PostRepository postRepository, PostHashtagRepository hashtags, PostViews postViews,
                             CurrentUser currentUser, Visibility visibility) {
        this.postRepository = postRepository;
        this.hashtags = hashtags;
        this.postViews = postViews;
        this.currentUser = currentUser;
        this.visibility = visibility;
    }

    @Operation(operationId = "listHashtagPosts", summary = "ハッシュタグの付いた投稿",
            description = "新しい順に20件。タグは # を付けずに渡す(付けてもよい)。大文字・小文字は区別しない。"
                    + "ブロック・ミュート中の人の投稿は出さない")
    @ApiErrors(400)
    @GetMapping("/hashtags/{tag}/posts")
    public PageResponse posts(@PathVariable String tag,
                              @Parameter(description = "前のページの nextCursor(投稿 ID)") @RequestParam(required = false) Long before,
                              HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        String normalized = Hashtags.normalize(tag);
        if (normalized.isEmpty() || normalized.length() > Hashtags.TAG_MAX) {
            throw ApiException.badRequest("ハッシュタグは1〜" + Hashtags.TAG_MAX + "文字にしてください");
        }
        Set<String> hidden = visibility.hidden(userId);
        Limit limit = Limit.of(PostViews.PAGE_SIZE + 1);
        return postViews.page(before == null
                ? postRepository.findByHashtag(normalized, hidden, limit)
                : postRepository.findByHashtagBefore(normalized, hidden, before, limit), userId);
    }

    @Operation(operationId = "listTrends", summary = "トレンド",
            description = "この24時間に投稿されたうち、使われた投稿の多いハッシュタグを10個まで")
    @GetMapping("/trends")
    public List<Trend> trends(HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        return hashtags.countSince(Instant.now().minus(TRENDS_WINDOW), Limit.of(TRENDS_MAX)).stream()
                .map(row -> new Trend((String) row[0], (Long) row[1]))
                .toList();
    }
}
