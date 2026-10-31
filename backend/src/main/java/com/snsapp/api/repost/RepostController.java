package com.snsapp.api.repost;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.post.Post;
import com.snsapp.api.post.PostController;
import com.snsapp.api.post.PostDtos.RepostResponse;
import com.snsapp.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** リポスト(F-13)。他人の投稿を、自分のフォロワーのホームタイムラインと自分のユーザーページに流す */
@RestController
@RequestMapping("/api")
public class RepostController {

    private final RepostRepository repostRepository;
    private final UserRepository userRepository;
    private final PostController postController;
    private final CurrentUser currentUser;

    public RepostController(RepostRepository repostRepository, UserRepository userRepository,
                            PostController postController, CurrentUser currentUser) {
        this.repostRepository = repostRepository;
        this.userRepository = userRepository;
        this.postController = postController;
        this.currentUser = currentUser;
    }

    /** リポストする。もうリポスト済みならそのまま(何度押しても1回分)。自分の投稿はリポストできない */
    @PostMapping("/posts/{id}/repost")
    public RepostResponse repost(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Post post = postController.findPost(id);
        if (post.getUser().getId().equals(userId)) {
            throw ApiException.badRequest("自分の投稿はリポストできません");
        }
        if (!repostRepository.existsByUserIdAndPostId(userId, id)) {
            try {
                repostRepository.saveAndFlush(new Repost(userRepository.getReferenceById(userId), post));
            } catch (DataIntegrityViolationException e) {
                // 上の確認と保存の間に同じリポストが入った(連打)。一意制約で弾かれたので、リポスト済みとして扱う
            }
        }
        return new RepostResponse(true, repostRepository.countByPostId(id));
    }

    /** リポストを取り消す。リポストしていなければそのまま */
    @DeleteMapping("/posts/{id}/repost")
    public RepostResponse unrepost(@PathVariable Long id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        postController.findPost(id);
        repostRepository.deleteByUserIdAndPostId(userId, id);
        return new RepostResponse(false, repostRepository.countByPostId(id));
    }
}
