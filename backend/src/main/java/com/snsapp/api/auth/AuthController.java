package com.snsapp.api.auth;

import com.snsapp.api.auth.AuthDtos.LoginRequest;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.RegisterRequest;
import com.snsapp.api.user.UserDtos.UserResponse;
import com.snsapp.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // 1つのメールアドレスに対して、この回数を超えるログインは一定時間受け付けない(パスワードの総当たり対策)。
    // 数えるのはメールアドレスごと(IP ごとにすると、同じ回線の人や E2E テストの多数のユーザーまで止まる)
    public static final int LOGIN_ATTEMPTS = 10;
    public static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;

    // 未登録のメールアドレスでも照合に使うダミーのハッシュ
    private final String dummyPasswordHash;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, CurrentUser currentUser,
                          RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("このメールアドレスは既に登録されています: " + email);
        }
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("このユーザー名は既に使われています: @" + request.username());
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.username(),
                request.displayName().strip());
        // 上の存在チェックとこの保存の間に同じメールアドレス・ユーザー名が登録されると一意制約違反になる。その場合も409で返す
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("このメールアドレスまたはユーザー名は既に使われています");
        }
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        if (!rateLimiter.tryAcquire("login:" + email, LOGIN_ATTEMPTS, LOGIN_WINDOW)) {
            throw ApiException.tooManyRequests("ログインの試行回数が多すぎます。5分ほど待ってからもう一度お試しください");
        }
        Optional<User> found = userRepository.findByEmail(email);
        // BCryptの照合は意図的に遅い。未登録のときに照合を省くと応答が速くなり、登録の有無を推測されるため、必ず1回照合する
        String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        User user = found.filter(u -> matches)
                .orElseThrow(() -> ApiException.unauthorized("メールアドレスまたはパスワードが正しくありません"));
        // ログイン前から存在するセッションIDを使い回さない(セッション固定攻撃対策)
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        currentUser.signIn(httpRequest, user);
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/me")
    public UserResponse me(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
    }
}
