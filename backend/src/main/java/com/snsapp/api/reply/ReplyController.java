package com.snsapp.api.reply;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.post.Post;
import com.snsapp.api.post.PostController;
import com.snsapp.api.post.PostDtos.CreateRequest;
import com.snsapp.api.user.UserDtos.PublicUser;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@Tag(name = OpenApiConfig.TAG_REPLY)
public class ReplyController {

    public static final int PAGE_SIZE = 20;

    public record ReplyResponse(String id, String postId, String body, Instant createdAt, PublicUser user) {
        static ReplyResponse from(Reply reply) {
            return new ReplyResponse(reply.getId().toString(), reply.getPost().getId().toString(), reply.getBody(),
                    reply.getCreatedAt(), PublicUser.from(reply.getUser()));
        }
    }

    /** 1ページ分のリプライ(古い順)と、「もっと見る」で続きを読めるか */
    public record ReplyPage(List<ReplyResponse> replies, boolean hasMore) {
    }

    private final ReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final PostController postController;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;
    private final Visibility visibility;

    public ReplyController(ReplyRepository replyRepository, UserRepository userRepository,
                           PostController postController, CurrentUser currentUser, RateLimiter rateLimiter,
                           Visibility visibility) {
        this.visibility = visibility;
        this.replyRepository = replyRepository;
        this.userRepository = userRepository;
        this.postController = postController;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    /** 投稿へのリプライを古い順に。after(リプライ ID)を渡すと、それより新しいものから */
    @Operation(operationId = "listReplies", summary = "投稿へのリプライ一覧",
            description = "古い順に20件")
    @ApiErrors({400, 404})
    @GetMapping("/posts/{postId}/replies")
    public ReplyPage list(@PathVariable Long postId,
                          @Parameter(description = "前のページの最後のリプライの ID。これより新しいものから読む")
                          @RequestParam(required = false) Long after,
                          HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        postController.findPost(postId, userId);
        // ブロック・ミュートしている人のリプライは出さない(F-20)
        Set<String> hidden = visibility.hidden(userId);
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Reply> fetched = after == null
                ? replyRepository.findByPostIdAndUserIdNotInOrderByIdAsc(postId, hidden, limit)
                : replyRepository.findByPostIdAndUserIdNotInAndIdGreaterThanOrderByIdAsc(postId, hidden, after, limit);
        return new ReplyPage(fetched.stream().limit(PAGE_SIZE).map(ReplyResponse::from).toList(),
                fetched.size() > PAGE_SIZE);
    }

    @Operation(operationId = "createReply", summary = "リプライする",
            description = "本文は140字まで(絵文字も1字と数える)。1人1分に10回まで")
    @ApiErrors({400, 404, 429})
    @PostMapping("/posts/{postId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public ReplyResponse create(@PathVariable Long postId, @RequestBody CreateRequest request,
                                HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Post post = postController.findPost(postId, userId);
        String body = PostController.bodyValue(request.body(), "リプライ");
        // 入力の確認が済んでから回数に数える(空のリプライで弾かれた分は数えない)
        if (!rateLimiter.tryAcquire("reply:" + userId, PostController.POSTS_PER_WINDOW, PostController.POST_WINDOW)) {
            throw ApiException.tooManyRequests("リプライが多すぎます。1分ほど待ってからもう一度お試しください");
        }
        Reply reply = new Reply(post, userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません")), body);
        return ReplyResponse.from(replyRepository.save(reply));
    }

    // 自分のリプライだけ消せる。他人のものは投稿と同じく 404
    @Operation(operationId = "deleteReply", summary = "リプライを消す",
            description = "自分のリプライだけ消せる")
    @ApiErrors({400, 404})
    @DeleteMapping("/replies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (replyRepository.deleteOwn(id, userId) == 0) {
            throw ApiException.notFound("リプライが見つかりません: " + id);
        }
    }
}
