package com.snsapp.api.auth;

/** 確認リンクを届ける。送れなければ ApiException(503)を投げる */
public interface VerificationMailer {

    /** メールアドレスの確認(登録・変更) */
    void send(String to, String link);

    /** パスワードの再設定(F-18) */
    void sendPasswordReset(String to, String link);
}
