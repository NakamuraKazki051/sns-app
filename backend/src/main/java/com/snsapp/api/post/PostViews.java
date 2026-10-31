package com.snsapp.api.post;

import com.snsapp.api.common.ApiException;
import com.snsapp.api.like.PostLikeRepository;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostDtos.PostResponse;
import com.snsapp.api.reply.ReplyRepository;
import com.snsapp.api.repost.Repost;
import com.snsapp.api.repost.RepostRepository;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.PublicUser;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投稿に、いいね数・リプライ数・リポスト数・自分がいいね/リポスト済みかを付けて返す。
 * 投稿ごとに数えに行く(N+1)と1ページで100回近く DB に問い合わせるので、1ページ分をまとめて5回で数える
 */
@Component
public class PostViews {

    /** 1ページの件数(タイムライン・ユーザーページ共通) */
    public static final int PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final RepostRepository repostRepository;

    public PostViews(PostRepository postRepository, PostLikeRepository likeRepository,
                     ReplyRepository replyRepository, RepostRepository repostRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.replyRepository = replyRepository;
        this.repostRepository = repostRepository;
    }

    /** タイムラインの1行: 投稿と、リポストとして出すならリポストした人 */
    private record Entry(Post post, User repostedBy) {
    }

    /**
     * 投稿とリポストを混ぜたタイムラインの「もっと見る」の目印。
     * 投稿とリポストは別々のテーブルなので、それぞれどこまで出したか(最後に出した ID)を持つ。null はまだ1件も出していない
     */
    public record FeedCursor(Long postBefore, Long repostBefore) {

        public static FeedCursor parse(String value) {
            if (value == null || value.isBlank()) {
                return new FeedCursor(null, null);
            }
            String[] parts = value.split(":", -1);
            try {
                if (parts.length == 2) {
                    return new FeedCursor(parts[0].isEmpty() ? null : Long.valueOf(parts[0]),
                            parts[1].isEmpty() ? null : Long.valueOf(parts[1]));
                }
            } catch (NumberFormatException e) {
                // 下で 400 にする
            }
            throw ApiException.badRequest("before の形式が正しくありません: " + value);
        }

        @Override
        public String toString() {
            return (postBefore == null ? "" : postBefore) + ":" + (repostBefore == null ? "" : repostBefore);
        }
    }

    /** 投稿だけの一覧。1件多く読んだ結果から、1ページ分と続きの目印(最後の投稿の ID)を作る */
    public PageResponse page(List<Post> fetched, String viewerId) {
        List<Post> posts = fetched.stream().limit(PAGE_SIZE).toList();
        boolean hasMore = fetched.size() > PAGE_SIZE;
        return new PageResponse(toResponses(posts.stream().map(p -> new Entry(p, null)).toList(), viewerId),
                hasMore, hasMore ? posts.getLast().getId().toString() : null);
    }

    /** この人たちの投稿とリポストを混ぜた1ページ(ホームタイムライン・ユーザーページ)。before は前のページの nextCursor */
    public PageResponse feedOf(Collection<String> userIds, String before, String viewerId) {
        FeedCursor cursor = FeedCursor.parse(before);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Post> posts = cursor.postBefore() == null
                ? postRepository.findByUserIdInOrderByIdDesc(userIds, limit)
                : postRepository.findByUserIdInAndIdLessThanOrderByIdDesc(userIds, cursor.postBefore(), limit);
        List<Repost> reposts = cursor.repostBefore() == null
                ? repostRepository.findByUserIdInOrderByIdDesc(userIds, limit)
                : repostRepository.findByUserIdInAndIdLessThanOrderByIdDesc(userIds, cursor.repostBefore(), limit);
        return feed(posts, reposts, cursor, viewerId);
    }

    /**
     * 投稿とリポストを、出来事の新しい順(投稿は投稿した時刻、リポストはリポストした時刻)に混ぜて1ページにする。
     * それぞれ1ページ分より1件多く読んでおけば、混ぜたうちの先頭1ページ分は必ずこの中にある。
     * 投稿・リポストそれぞれの中では ID の順に取り出すので、次のページは「最後に出した ID より前」から読めばよい
     */
    PageResponse feed(List<Post> posts, List<Repost> reposts, FeedCursor cursor, String viewerId) {
        List<Entry> entries = new ArrayList<>();
        int p = 0;
        int r = 0;
        Long postBefore = cursor.postBefore();
        Long repostBefore = cursor.repostBefore();
        while (entries.size() < PAGE_SIZE && (p < posts.size() || r < reposts.size())) {
            boolean takePost = r >= reposts.size() || (p < posts.size()
                    && !posts.get(p).getCreatedAt().isBefore(reposts.get(r).getCreatedAt()));
            if (takePost) {
                Post post = posts.get(p++);
                entries.add(new Entry(post, null));
                postBefore = post.getId();
            } else {
                Repost repost = reposts.get(r++);
                entries.add(new Entry(repost.getPost(), repost.getUser()));
                repostBefore = repost.getId();
            }
        }
        boolean hasMore = p < posts.size() || r < reposts.size();
        return new PageResponse(toResponses(entries, viewerId), hasMore,
                hasMore ? new FeedCursor(postBefore, repostBefore).toString() : null);
    }

    public PostResponse one(Post post, String viewerId) {
        return toResponses(List.of(new Entry(post, null)), viewerId).getFirst();
    }

    private List<PostResponse> toResponses(List<Entry> entries, String viewerId) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Long> ids = entries.stream().map(e -> e.post().getId()).distinct().toList();
        Map<Long, Long> likes = counts(likeRepository.countByPostIds(ids));
        Map<Long, Long> replies = counts(replyRepository.countByPostIds(ids));
        Map<Long, Long> reposts = counts(repostRepository.countByPostIds(ids));
        Set<Long> liked = new HashSet<>(likeRepository.findLikedPostIds(viewerId, ids));
        Set<Long> reposted = new HashSet<>(repostRepository.findRepostedPostIds(viewerId, ids));
        return entries.stream()
                .map(e -> {
                    Post post = e.post();
                    Long id = post.getId();
                    return new PostResponse(id.toString(), post.getBody(), post.getCreatedAt(),
                            PublicUser.from(post.getUser()), likes.getOrDefault(id, 0L),
                            replies.getOrDefault(id, 0L), liked.contains(id), reposts.getOrDefault(id, 0L),
                            reposted.contains(id), e.repostedBy() == null ? null : PublicUser.from(e.repostedBy()));
                })
                .toList();
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
