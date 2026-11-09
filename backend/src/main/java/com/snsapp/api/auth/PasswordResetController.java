package com.snsapp.api.auth;

import com.snsapp.api.auth.AuthDtos.ForgotPasswordRequest;
import com.snsapp.api.auth.AuthDtos.ResetPasswordRequest;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.ClientIp;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.PublicApi;
import com.snsapp.api.common.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** パスワードの再設定(F-18)。ログインしていない人が使う */
@RestController
@RequestMapping("/api/auth")
@Tag(name = OpenApiConfig.TAG_AUTH)
public class PasswordResetController {

    // 他人のアドレスにメールを送り続けられないよう、宛先ごとと IP ごとに数える
    static final int REQUESTS_PER_EMAIL = 3;
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);

    private final PasswordResetService resets;
    private final RateLimiter rateLimiter;
    private final ClientIp clientIp;
    private final int perIpPerHour;

    public PasswordResetController(PasswordResetService resets, RateLimiter rateLimiter, ClientIp clientIp,
                                   @Value("${app.register.per-hour}") int perIpPerHour) {
        this.resets = resets;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.perIpPerHour = perIpPerHour;
    }

    @Operation(operationId = "forgotPassword", summary = "パスワード再設定のメールを送る",
            description = "登録の有無が分からないよう、送らなかったときも 204 を返す。"
                    + "リンクは1時間有効。1つのアドレスにつき1時間に3回まで")
    @PublicApi
    @ApiErrors({400, 429, 503})
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        if (!rateLimiter.tryAcquire("forgot:" + email, REQUESTS_PER_EMAIL, REQUEST_WINDOW)
                || !rateLimiter.tryAcquire("forgot-ip:" + clientIp.of(httpRequest), perIpPerHour, REQUEST_WINDOW)) {
            throw ApiException.tooManyRequests("送信の回数が多すぎます。1時間ほど待ってからもう一度お試しください");
        }
        resets.request(email);
    }

    @Operation(operationId = "resetPassword", summary = "新しいパスワードを決める",
            description = "再設定のメールのリンクに入っているトークンと、新しいパスワードを送る。"
                    + "トークンは1回だけ使える。すべての端末でログアウトになるので、新しいパスワードでログインし直す")
    @PublicApi
    @ApiErrors(400)
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resets.reset(request.token(), request.newPassword());
    }
}
