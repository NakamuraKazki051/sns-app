package com.snsapp.api.post;

import com.snsapp.api.user.UserDtos.PublicUser;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public class PostDtos {

    private PostDtos() {
    }

    // 本文の検証(空白だけ・140字超え)は文字の数え方が特殊なので PostController.bodyValue で行う
    public record CreateRequest(@Schema(description = "本文。140字まで(絵文字も1字と数える)") String body) {
    }

    /** 画像付きの投稿の形(API 仕様書のためだけに使う。実際は PostController.createWithImage が1項目ずつ受け取る) */
    public record ImagePostForm(
            @Schema(description = "本文。140字まで。画像があれば空でもよい") String body,
            @Schema(description = "JPEG か PNG、5MBまで", type = "string", format = "binary",
                    requiredMode = Schema.RequiredMode.REQUIRED) String image) {
    }

    /**
     * 投稿1件。JavaScript の数値で誤差が出ないよう、ID は文字列で返す。
     * タイムラインでリポストとして出すときは repostedBy にリポストした人が入る(元の投稿として出すときは null)
     */
    public record PostResponse(@Schema(description = "投稿 ID(文字列)") String id, String body, Instant createdAt,
                               PublicUser user, long likesCount, long repliesCount, boolean likedByMe,
                               long repostsCount, boolean repostedByMe,
                               @Schema(description = "リポストした人。リポストとして出すときだけ入り、それ以外は null") PublicUser repostedBy,
                               @Schema(description = "画像。無ければ null") ImageResponse image) {
    }

    /** 投稿の画像(無ければ PostResponse.image が null)。幅と高さで、読み込む前に枠の形を決められる */
    public record ImageResponse(@Schema(description = "画像の URL。読むにはログインが要る") String url, int width, int height) {

        static ImageResponse of(Post post) {
            PostImage image = post.getImage();
            return image == null ? null
                    : new ImageResponse("/api/posts/" + post.getId() + "/image", image.width(), image.height());
        }
    }

    /**
     * 今見ている一覧の先頭より新しい、他の人の投稿(新着のお知らせ用)。
     * count は数(上限 NEW_POSTS_MAX)、users は新しい投稿の投稿者(重複なしで3人まで、アイコンを並べる)
     */
    public record NewPostsResponse(@Schema(description = "新着の数。100で止まる") long count,
                                   @Schema(description = "新着の投稿者。重複なしで3人まで") List<PublicUser> users) {
    }

    /**
     * 1ページ分の投稿と、「もっと見る」で続きを読めるか。
     * nextCursor は次のページを読むときに before に渡す値(続きがなければ null)
     */
    public record PageResponse(List<PostResponse> posts, boolean hasMore,
                               @Schema(description = "次のページを読むときに before に渡す値。続きが無ければ null") String nextCursor) {
    }

    /** いいね・取り消しの結果 */
    public record LikeResponse(boolean liked, long likesCount) {
    }

    /** リポスト・取り消しの結果 */
    public record RepostResponse(boolean reposted, long repostsCount) {
    }
}
