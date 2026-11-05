package com.snsapp.api.auth;

import com.snsapp.api.auth.AuthDtos.LoginRequest;
import com.snsapp.api.auth.AuthDtos.ResendVerificationRequest;
import com.snsapp.api.auth.AuthDtos.VerifyEmailRequest;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.ClientIp;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.PublicApi;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.RegisterRequest;
import com.snsapp.api.user.UserDtos.UserResponse;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = OpenApiConfig.TAG_AUTH)
public class AuthController {

    // 1つのメールアドレスに対して、この回数を超えるログインは一定時間受け付けない(パスワードの総当たり対策)。
    // 数えるのはメールアドレスごと(IP ごとにすると、同じ回線の人や E2E テストの多数のユーザーまで止まる)
    public static final int LOGIN_ATTEMPTS = 10;
    public static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);

    // アカウントの大量作成の対策。1つの IP から1時間に登録できる回数(AWS は5回。ローカルと E2E テストは実質なし)
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    // 確認メールの再送。他人のアドレスにメールを送り続けられないよう、宛先ごとに数える
    private static final int RESEND_PER_EMAIL = 3;
    private static final Duration RESEND_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;
    private final ClientIp clientIp;
    private final EmailVerificationService emailVerification;
    private final int registerPerHour;

    // 未登録のメールアドレスでも照合に使うダミーのハッシュ
    private final String dummyPasswordHash;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, CurrentUser currentUser,
                          RateLimiter rateLimiter, ClientIp clientIp, EmailVerificationService emailVerification,
                          @Value("${app.register.per-hour}") int registerPerHour) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.emailVerification = emailVerification;
        this.registerPerHour = registerPerHour;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Operation(operationId = "register", summary = "ユーザー登録",
            description = "登録だけではログインしない。確認メールのリンクを開いたあと、ログインの API を呼ぶ。"
                    + "1つの IP から1時間に5回まで(入力の誤りは数えない)")
    @PublicApi
    @ApiErrors({400, 409, 429, 503})
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        // 使用済みのメールアドレスも数える(数えないと、登録の有無を際限なく調べられる)
        if (!rateLimiter.tryAcquire("register:" + clientIp.of(httpRequest), registerPerHour, REGISTER_WINDOW)) {
            throw ApiException.tooManyRequests("登録の回数が多すぎます。1時間ほど待ってからもう一度お試しください");
        }
        emailVerification.deleteExpiredRegistrations();
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("このメールアドレスは既に登録されています: " + email);
        }
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("このユーザー名は既に使われています: @" + request.username());
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.username(),
                request.displayName().strip());
        if (!emailVerification.isRequired()) {
            user.verifyEmail(email, Instant.now());
        }
        // 上の存在チェックとこの保存の間に同じメールアドレス・ユーザー名が登録されると一意制約違反になる。その場合も409で返す
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("このメールアドレスまたはユーザー名は既に使われています");
        }
        if (emailVerification.isRequired()) {
            try {
                emailVerification.send(saved, email);
            } catch (ApiException e) {
                // メールが届かないとログインできないアカウントになるので残さない。やり直せば同じ内容で登録できる
                userRepository.delete(saved);
                throw e;
            }
        }
        return UserResponse.from(saved);
    }

    @Operation(operationId = "verifyEmail", summary = "メールアドレスの確認",
            description = "確認メールのリンクに入っているトークンを送る。トークンは1回だけ使える")
    @PublicApi
    @ApiErrors({400, 409})
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerification.verify(request.token());
    }

    @Operation(operationId = "resendVerification", summary = "確認メールの再送",
            description = "登録の有無が分からないよう、送らなかったときも 204 を返す。1つのアドレスにつき1時間に3回まで")
    @PublicApi
    @ApiErrors({400, 429})
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        if (!rateLimiter.tryAcquire("resend:" + email, RESEND_PER_EMAIL, RESEND_WINDOW)
                || !rateLimiter.tryAcquire("resend-ip:" + clientIp.of(httpRequest), registerPerHour, RESEND_WINDOW)) {
            throw ApiException.tooManyRequests("再送の回数が多すぎます。1時間ほど待ってからもう一度お試しください");
        }
        userRepository.findByEmail(email).filter(user -> !user.isEmailVerified()).ifPresent(user -> {
            try {
                emailVerification.send(user, email);
            } catch (ApiException e) {
                // ここでエラーを返すと、未確認の登録があると分かってしまう(ログには残っている)
            }
        });
    }

    @Operation(operationId = "login", summary = "ログイン",
            description = "成功すると Cookie(SNS_SESSION)が付く。1つのメールアドレスにつき5分に10回まで")
    @PublicApi
    @ApiErrors({400, 401, 403, 429})
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
        // パスワードが合った人にだけ伝える(他の人には、登録の有無も確認の状態も分からない)
        if (emailVerification.isRequired() && !user.isEmailVerified()) {
            throw ApiException.forbidden("メールアドレスの確認が済んでいません。届いたメールのリンクを開いてください");
        }
        // ログイン前から存在するセッションIDを使い回さない(セッション固定攻撃対策)
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        currentUser.signIn(httpRequest, user);
        return UserResponse.from(user);
    }

    @Operation(operationId = "logout", summary = "ログアウト",
            description = "ログインしていなくても 204 を返す")
    @PublicApi
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Operation(operationId = "getCurrentUser", summary = "ログイン中の自分の情報")
    @GetMapping("/me")
    public UserResponse me(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
    }
}
