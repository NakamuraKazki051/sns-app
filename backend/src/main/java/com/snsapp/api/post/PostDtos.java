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

    /**
     * 投稿1件。JavaScript の数値で誤差が出ないよう、ID は文字列で返す。
     * タイムラインでリポストとして出すときは repostedBy にリポストした人が入る(元の投稿として出すときは null)
     */
    public record PostResponse(String id, String body, Instant createdAt, PublicUser user, long likesCount,
                               long repliesCount, boolean likedByMe, long repostsCount, boolean repostedByMe,
                               PublicUser repostedBy) {
    }

    /**
     * 1ページ分の投稿と、「もっと見る」で続きを読めるか。
     * nextCursor は次のページを読むときに before に渡す値(続きがなければ null)
     */
    public record PageResponse(List<PostResponse> posts, boolean hasMore, String nextCursor) {
    }

    /** いいね・取り消しの結果 */
    public record LikeResponse(boolean liked, long likesCount) {
    }

    /** リポスト・取り消しの結果 */
    public record RepostResponse(boolean reposted, long repostsCount) {
    }
}
