package com.snsapp.api.auth;

/** 確認リンクを届ける。送れなければ ApiException(503)を投げる */
public interface VerificationMailer {
    void send(String to, String link);
}
