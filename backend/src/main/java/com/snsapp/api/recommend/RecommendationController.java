package com.snsapp.api.recommend;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.follow.FollowRepository;
import com.snsapp.api.follow.FollowRequestRepository;
import com.snsapp.api.post.Post;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostRepository;
import com.snsapp.api.post.PostViews;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * おすすめ(F-27)。フォローするとよさそうな人と、よく反応されている最近の投稿。
 * 見えない人(ブロック・ミュート・フォローしていない鍵アカウント)は出さない
 */
@RestController
@RequestMapping("/api")
@Tag(name = OpenApiConfig.TAG_POST)
public class RecommendationController {

    static final int USERS_MAX = 5;
    static final int POSTS_MAX = 20;
    /** おすすめの投稿は、この期間に投稿されたものから選ぶ */
    static final Duration POSTS_WINDOW = Duration.ofDays(3);
    /** 新しい投稿をこの件数だけ見て、その中から選ぶ(投稿が何万件あっても読む量を増やさない) */
    static final int POST_CANDIDATES = 500;

    public enum Reason {
        /** フォロー中の人がフォローしている */
        FOLLOWED_BY_FOLLOWING,
        /** フォロワーが多い */
        POPULAR,
        /** 最近登録した */
        NEW
    }

    public record RecommendedUser(String id, String username, String displayName,
                                  @Schema(types = {"string", "null"}, format = "date-time") Instant iconUpdatedAt,
                                  @Schema(types = {"string", "null"}) String bio,
                                  @Schema(description = "おすすめの理由") Reason reason,
                                  @Schema(description = "FOLLOWED_BY_FOLLOWING なら、その人をフォローしているフォロー中の人の数") long mutualCount) {
        static RecommendedUser of(User user, Reason reason, long mutualCount) {
            return new RecommendedUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getIconUpdatedAt(),
                    user.getBio(), reason, mutualCount);
        }
    }

    private final FollowRepository follows;
    private final FollowRequestRepository followRequests;
    private final UserRepository users;
    private final PostRepository posts;
    private final PostViews postViews;
    private final CurrentUser currentUser;
    private final Visibility visibility;

    public RecommendationController(FollowRepository follows, FollowRequestRepository followRequests,
                                    UserRepository users, PostRepository posts, PostViews postViews,
                                    CurrentUser currentUser, Visibility visibility) {
        this.follows = follows;
        this.followRequests = followRequests;
        this.users = users;
        this.posts = posts;
        this.postViews = postViews;
        this.currentUser = currentUser;
        this.visibility = visibility;
    }

    @Operation(operationId = "recommendUsers", summary = "おすすめのユーザー",
            description = "5人まで。フォロー中の人がよくフォローしている人を先に、足りなければフォロワーの多い人、最近登録した人。"
                    + "自分・フォロー済み・リクエスト中・ブロック中の人は出さない")
    @GetMapping("/recommendations/users")
    public List<RecommendedUser> users(HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        Set<String> excluded = new HashSet<>(visibility.blockRelated(me));
        excluded.add(me);
        excluded.addAll(follows.findFolloweeIds(me));
        excluded.addAll(followRequests.findRequestedIds(me));

        // 理由ごとに候補を足していき、5人になったら止める(先に足した理由を優先する)
        Map<String, Candidate> picked = new LinkedHashMap<>();
        for (Object[] row : follows.findFollowedByFollowing(me, excluded, Limit.of(USERS_MAX))) {
            picked.putIfAbsent((String) row[0], new Candidate(Reason.FOLLOWED_BY_FOLLOWING, (Long) row[1]));
        }
        if (picked.size() < USERS_MAX) {
            Set<String> skip = new HashSet<>(excluded);
            skip.addAll(picked.keySet());
            for (Object[] row : follows.findMostFollowed(skip, Limit.of(USERS_MAX - picked.size()))) {
                picked.putIfAbsent((String) row[0], new Candidate(Reason.POPULAR, 0));
            }
        }
        if (picked.size() < USERS_MAX) {
            Set<String> skip = new HashSet<>(excluded);
            skip.addAll(picked.keySet());
            for (String id : users.findNewestVerifiedIds(skip, Limit.of(USERS_MAX - picked.size()))) {
                picked.putIfAbsent(id, new Candidate(Reason.NEW, 0));
            }
        }
        Map<String, User> found = users.findAllById(picked.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<RecommendedUser> result = new ArrayList<>();
        picked.forEach((id, candidate) -> {
            User user = found.get(id);
            if (user != null && user.isEmailVerified()) {
                result.add(RecommendedUser.of(user, candidate.reason(), candidate.mutualCount()));
            }
        });
        return result;
    }

    @Operation(operationId = "getRecommendedTimeline", summary = "おすすめの投稿",
            description = "この3日間の投稿から、いいね・リポスト(2倍)・リプライの多い順に20件(同じなら新しい順)。"
                    + "自分の投稿と、見えない人の投稿は出さない。続きは無い(hasMore は false)")
    @GetMapping("/timeline/recommended")
    public PageResponse posts(HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        Set<String> hidden = new HashSet<>(visibility.postHidden(me));
        hidden.add(me);
        List<Long> ids = posts.findRecommendedIds(hidden, Instant.now().minus(POSTS_WINDOW), POST_CANDIDATES, POSTS_MAX);
        if (ids.isEmpty()) {
            return new PageResponse(List.of(), false, null);
        }
        Map<Long, Post> byId = posts.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> ordered = ids.stream().map(byId::get).filter(p -> p != null).toList();
        PageResponse page = postViews.page(ordered, me);
        return new PageResponse(page.posts(), false, null);
    }

    private record Candidate(Reason reason, long mutualCount) {
    }
}
