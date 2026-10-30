package com.snsapp.api.post;

import com.snsapp.api.like.PostLikeRepository;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostDtos.PostResponse;
import com.snsapp.api.reply.ReplyRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投稿に、いいね数・リプライ数・自分がいいね済みかを付けて返す。
 * 投稿ごとに数えに行く(N+1)と1ページで60回近く DB に問い合わせるので、1ページ分をまとめて3回で数える
 */
@Component
public class PostViews {

    /** 1ページの件数(タイムライン・ユーザーページ共通) */
    public static final int PAGE_SIZE = 20;

    private final PostLikeRepository likeRepository;
    private final ReplyRepository replyRepository;

    public PostViews(PostLikeRepository likeRepository, ReplyRepository replyRepository) {
        this.likeRepository = likeRepository;
        this.replyRepository = replyRepository;
    }

    /** 1件多く読んだ結果から、1ページ分と「続きがあるか」を作る */
    public PageResponse page(List<Post> fetched, String viewerId) {
        return new PageResponse(toResponses(fetched.stream().limit(PAGE_SIZE).toList(), viewerId),
                fetched.size() > PAGE_SIZE);
    }

    public PostResponse one(Post post, String viewerId) {
        return toResponses(List.of(post), viewerId).getFirst();
    }

    private List<PostResponse> toResponses(List<Post> posts, String viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();
        Map<Long, Long> likes = counts(likeRepository.countByPostIds(ids));
        Map<Long, Long> replies = counts(replyRepository.countByPostIds(ids));
        Set<Long> liked = new HashSet<>(likeRepository.findLikedPostIds(viewerId, ids));
        return posts.stream()
                .map(p -> PostResponse.from(p, likes.getOrDefault(p.getId(), 0L),
                        replies.getOrDefault(p.getId(), 0L), liked.contains(p.getId())))
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
