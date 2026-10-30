package com.snsapp.api.user;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.post.PostController;
import com.snsapp.api.post.PostDtos.PageResponse;
import com.snsapp.api.post.PostRepository;
import com.snsapp.api.user.UserDtos.ProfileResponse;
import com.snsapp.api.user.UserDtos.UpdateRequest;
import com.snsapp.api.user.UserDtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserController(UserRepository userRepository, PostRepository postRepository, PasswordEncoder passwordEncoder,
                          CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    /** ユーザーページ: 表示名と投稿数 */
    @GetMapping("/{username}")
    public ProfileResponse show(@PathVariable String username, HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        User user = findUser(username);
        return new ProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                postRepository.countByUserId(user.getId()), user.getCreatedAt());
    }

    /** その人の投稿一覧。before(投稿 ID)を渡すと、それより古いものから */
    @GetMapping("/{username}/posts")
    public PageResponse posts(@PathVariable String username, @RequestParam(required = false) Long before,
                              HttpServletRequest httpRequest) {
        currentUser.require(httpRequest);
        User user = findUser(username);
        Limit limit = Limit.of(PostController.PAGE_SIZE + 1);
        return PostController.toPage(before == null
                ? postRepository.findByUserIdOrderByIdDesc(user.getId(), limit)
                : postRepository.findByUserIdAndIdLessThanOrderByIdDesc(user.getId(), before, limit));
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("現在のパスワードが正しくありません");
        }

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw ApiException.conflict("このメールアドレスは既に登録されています: " + email);
            }
            user.setEmail(email);
        }

        boolean passwordChanged = request.newPassword() != null && !request.newPassword().isBlank();
        if (passwordChanged) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("このメールアドレスは既に登録されています: " + user.getEmail());
        }
        if (passwordChanged) {
            // 他の端末のセッションはパスワード不一致で切れる。変更した本人のセッションだけ新しいパスワードで維持する
            currentUser.signIn(httpRequest, saved);
        }
        return UserResponse.from(saved);
    }

    // ユーザー名は大文字・小文字を区別しない(DB の照合順序による)
    private User findUser(String username) {
        return (username.matches(User.USERNAME_PATTERN) ? userRepository.findByUsername(username) : Optional.<User>empty())
                .orElseThrow(() -> ApiException.notFound("ユーザーが見つかりません: @" + username));
    }
}
