package com.snsapp.api.post;

import com.snsapp.api.user.UserDtos.PublicUser;

import java.time.Instant;
import java.util.List;

public class PostDtos {

    private PostDtos() {
    }

    // 本文の検証(空白だけ・140字超え)は文字の数え方が特殊なので PostController.bodyValue で行う
    public record CreateRequest(String body) {
    }

    // JavaScript の数値で誤差が出ないよう、ID は文字列で返す
    public record PostResponse(String id, String body, Instant createdAt, PublicUser user, long likesCount,
                               long repliesCount, boolean likedByMe) {
        public static PostResponse from(Post post, long likesCount, long repliesCount, boolean likedByMe) {
            return new PostResponse(post.getId().toString(), post.getBody(), post.getCreatedAt(),
                    PublicUser.from(post.getUser()), likesCount, repliesCount, likedByMe);
        }
    }

    /** 1ページ分の投稿と、「もっと見る」で続きを読めるか */
    public record PageResponse(List<PostResponse> posts, boolean hasMore) {
    }

    /** いいね・取り消しの結果 */
    public record LikeResponse(boolean liked, long likesCount) {
    }
}
