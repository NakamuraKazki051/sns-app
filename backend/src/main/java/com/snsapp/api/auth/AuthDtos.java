package com.snsapp.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    private AuthDtos() {
    }

    // 登録できる長さまで。メールアドレスはログイン回数を数えるキーとしてメモリに置くので、巨大な値を受け付けない
    public record LoginRequest(
            @NotBlank @Size(max = 255, message = "メールアドレスは255文字以内で入力してください") String email,
            @NotBlank @Size(max = 72, message = "パスワードは72文字以内で入力してください") String password
    ) {
    }

    public record VerifyEmailRequest(
            @NotBlank @Size(max = 100, message = "リンクが正しくないか、有効期限が切れています") String token
    ) {
    }

    public record ResendVerificationRequest(
            @NotBlank @Size(max = 255, message = "メールアドレスは255文字以内で入力してください") String email
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "メールアドレスを入力してください")
            @Size(max = 255, message = "メールアドレスは255文字以内で入力してください") String email
    ) {
    }

    // パスワードのルールは登録と同じ(8〜72文字。72バイトを超える日本語などは PasswordEncoderConfig が弾く)
    public record ResetPasswordRequest(
            @NotBlank @Size(max = 100, message = "リンクが正しくないか、有効期限が切れています") String token,
            @NotBlank @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください") String newPassword
    ) {
    }
}
