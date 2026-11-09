package com.snsapp.api.user;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.post.ImageStorage;
import com.snsapp.api.user.UserDtos.DeleteAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** アカウントの削除(F-17) */
@RestController
@RequestMapping("/api/users")
@Tag(name = OpenApiConfig.TAG_USER)
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    // ログインしたまま席を離れた端末で、パスワードを当てられないように数える
    static final int DELETE_ATTEMPTS = 5;
    private static final Duration DELETE_WINDOW = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final AccountDeletionService deletion;
    private final ImageStorage imageStorage;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;

    public AccountController(UserRepository userRepository, AccountDeletionService deletion, ImageStorage imageStorage,
                             PasswordEncoder passwordEncoder, CurrentUser currentUser, RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.deletion = deletion;
        this.imageStorage = imageStorage;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    @Operation(operationId = "deleteAccount", summary = "アカウントを削除する",
            description = "今のパスワードが要る。投稿・リプライ・いいね・リポスト・フォロー・アイコン・投稿の画像・DM も消え、元に戻せない。"
                    + "すべての端末でログアウトになる。5分に5回まで")
    @ApiErrors({400, 429})
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@Valid @RequestBody DeleteAccountRequest request, HttpServletRequest httpRequest,
                         HttpServletResponse httpResponse) {
        String userId = currentUser.require(httpRequest);
        if (!rateLimiter.tryAcquire("delete-account:" + userId, DELETE_ATTEMPTS, DELETE_WINDOW)) {
            throw ApiException.tooManyRequests("試行回数が多すぎます。5分ほど待ってからもう一度お試しください");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("パスワードが正しくありません");
        }
        List<String> imageKeys = deletion.delete(userId);
        // ユーザーがいなくなったので、どの端末の JWT も CurrentUser が断る。この端末の Cookie も消しておく
        currentUser.clearCookie(httpResponse);
        // DB から消えたあとで画像を消す(先に消すと、失敗したときに画像の無い投稿が残る)
        for (String key : imageKeys) {
            try {
                imageStorage.delete(key);
            } catch (Exception e) {
                log.warn("削除したアカウントの画像を消せませんでした: {}", key, e);
            }
        }
    }
}
