package com.snsapp.api.auth;

import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * 2段階認証の設定(F-24)。設定画面から、認証アプリの QR コードを読み取って有効にする。
 * 有効にすると、ログインでパスワードのあとに6桁のコード(かリカバリーコード)が要る
 */
@RestController
@RequestMapping("/api/me/two-factor")
@Tag(name = OpenApiConfig.TAG_ME)
public class TwoFactorController {

    // コードの総当たり対策。有効にする・外す・リカバリーコードを作り直すのを合わせて、1人5分に10回まで
    static final int ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    public record Status(@Schema(description = "有効か") boolean enabled,
                         @Schema(description = "まだ使っていないリカバリーコードの数(無効なら0)") long recoveryCodesLeft) {
    }

    public record Setup(@Schema(description = "秘密の鍵(Base32)。QR コードを読めないときに認証アプリへ手で入れる") String secret,
                        @Schema(description = "認証アプリに読ませる otpauth:// の URI。画面が QR コードにする") String uri) {
    }

    public record CodeRequest(
            @NotBlank(message = "認証コードを入力してください")
            @Size(max = 20, message = "認証コードが正しくありません") String code) {
    }

    public record DisableRequest(
            @NotBlank(message = "パスワードを入力してください") @Size(max = 72) String password,
            @NotBlank(message = "認証コードを入力してください")
            @Size(max = 20, message = "認証コードが正しくありません") String code) {
    }

    public record RecoveryCodes(@Schema(description = "リカバリーコード。今だけ見られる(サーバーにはハッシュしか残らない)") List<String> recoveryCodes) {
    }

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final TwoFactorService twoFactor;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;

    public TwoFactorController(UserRepository users, CurrentUser currentUser, TwoFactorService twoFactor,
                               PasswordEncoder passwordEncoder, RateLimiter rateLimiter) {
        this.users = users;
        this.currentUser = currentUser;
        this.twoFactor = twoFactor;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
    }

    @Operation(operationId = "getTwoFactor", summary = "2段階認証の状態")
    @GetMapping
    public Status status(HttpServletRequest httpRequest) {
        User user = me(httpRequest);
        return new Status(user.hasTwoFactor(), user.hasTwoFactor() ? twoFactor.recoveryCodesLeft(user.getId()) : 0);
    }

    @Operation(operationId = "setupTwoFactor", summary = "2段階認証の設定を始める",
            description = "新しい秘密の鍵を作って返す。まだ有効にはならない(有効にする API にコードを送ったとき有効になる)。"
                    + "もう有効なら 409")
    @ApiErrors(409)
    @PostMapping("/setup")
    public Setup setup(HttpServletRequest httpRequest) {
        User user = me(httpRequest);
        if (user.hasTwoFactor()) {
            throw ApiException.conflict("2段階認証はすでに有効です");
        }
        String secret = twoFactor.startSetup(user);
        users.save(user);
        return new Setup(secret, Totp.uri(TwoFactorService.ISSUER, user.getEmail(), secret));
    }

    @Operation(operationId = "enableTwoFactor", summary = "2段階認証を有効にする",
            description = "設定を始めたあと、認証アプリに出た6桁のコードを送る。合えば有効にし、リカバリーコード10個を返す"
                    + "(今だけ見られる)。1人5分に10回まで")
    @ApiErrors({400, 409, 429})
    @PostMapping("/enable")
    public RecoveryCodes enable(@Valid @RequestBody CodeRequest request, HttpServletRequest httpRequest) {
        User user = me(httpRequest);
        if (user.hasTwoFactor()) {
            throw ApiException.conflict("2段階認証はすでに有効です");
        }
        limit(user);
        if (user.getTotpPendingSecret() == null) {
            throw ApiException.badRequest("先に設定を始めてください");
        }
        List<String> codes = twoFactor.enable(user, request.code());
        if (codes == null) {
            throw ApiException.badRequest("認証コードが正しくありません。認証アプリの今のコードを入れてください");
        }
        users.save(user);
        return new RecoveryCodes(codes);
    }

    @Operation(operationId = "regenerateRecoveryCodes", summary = "リカバリーコードを作り直す",
            description = "認証アプリのコード(かまだ使っていないリカバリーコード)が要る。前のリカバリーコードは使えなくなる。1人5分に10回まで")
    @ApiErrors({400, 409, 429})
    @PostMapping("/recovery-codes")
    public RecoveryCodes regenerate(@Valid @RequestBody CodeRequest request, HttpServletRequest httpRequest) {
        User user = me(httpRequest);
        if (!user.hasTwoFactor()) {
            throw ApiException.conflict("2段階認証は有効になっていません");
        }
        limit(user);
        if (!twoFactor.verify(user, request.code())) {
            throw ApiException.badRequest("認証コードが正しくありません");
        }
        users.save(user);
        return new RecoveryCodes(twoFactor.newRecoveryCodes(user.getId()));
    }

    @Operation(operationId = "disableTwoFactor", summary = "2段階認証を外す",
            description = "パスワードと、認証アプリのコード(かまだ使っていないリカバリーコード)が要る。リカバリーコードも消す。1人5分に10回まで")
    @ApiErrors({400, 409, 429})
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Valid @RequestBody DisableRequest request, HttpServletRequest httpRequest) {
        User user = me(httpRequest);
        if (!user.hasTwoFactor()) {
            throw ApiException.conflict("2段階認証は有効になっていません");
        }
        limit(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("パスワードが正しくありません");
        }
        if (!twoFactor.verify(user, request.code())) {
            throw ApiException.badRequest("認証コードが正しくありません");
        }
        twoFactor.disable(user);
        users.save(user);
    }

    private User me(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return users.findById(userId).orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
    }

    private void limit(User user) {
        if (!rateLimiter.tryAcquire("two-factor:" + user.getId(), ATTEMPTS, WINDOW)) {
            throw ApiException.tooManyRequests("試した回数が多すぎます。5分ほど待ってからもう一度お試しください");
        }
    }
}
