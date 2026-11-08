package com.snsapp.api.auth;

import com.snsapp.api.common.ApiException;
import com.snsapp.api.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * ログイン中の人を、Cookie に入った JWT で確かめる。サーバー側には何も記録しない。
 * Cookie は HttpOnly(JavaScript から読めない)・SameSite=Lax。AWS(https)では Secure も付ける
 */
@Component
public class CurrentUser {

    public static final String COOKIE_NAME = "SNS_TOKEN";
    // 1つのリクエストの中で確かめ終わった人。RequestLogFilter がログに出す
    public static final String VERIFIED_KEY = CurrentUser.class.getName() + ".verified";
    private static final String ISSUED_AT_KEY = CurrentUser.class.getName() + ".issuedAt";
    // 作ってからこれより経った JWT は、操作のたびに作り直して期限を延ばす(毎回だと、すべての応答に Cookie が付く)
    static final Duration REFRESH_AFTER = Duration.ofMinutes(1);

    private final TokenService tokens;
    private final boolean secureCookie;

    public CurrentUser(TokenService tokens, @Value("${app.auth.cookie-secure}") boolean secureCookie) {
        this.tokens = tokens;
        this.secureCookie = secureCookie;
    }

    public String require(HttpServletRequest request) {
        if (request.getAttribute(VERIFIED_KEY) instanceof String verified) {
            return verified;
        }
        String token = readCookie(request);
        if (token == null) {
            throw ApiException.unauthorized("ログインが必要です");
        }
        TokenService.Verified verified = tokens.verify(token)
                .orElseThrow(() -> ApiException.unauthorized("ログインの有効期限が切れました。もう一度ログインしてください"));
        request.setAttribute(VERIFIED_KEY, verified.userId());
        request.setAttribute(ISSUED_AT_KEY, verified.issuedAt());
        return verified.userId();
    }

    /** ログインした人に JWT の Cookie を渡す。パスワードを変えたときも作り直す */
    public void signIn(HttpServletResponse response, User user) {
        writeCookie(response, tokens.issue(user.getId()), tokens.ttl());
    }

    /** ブラウザの Cookie を消す。JWT そのものは期限まで有効のまま(サーバーは覚えていないので取り消せない) */
    public void signOut(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    /** require() で確かめた JWT が古くなっていれば、作り直して期限を延ばす(LoginInterceptor が呼ぶ) */
    void refreshIfOld(HttpServletRequest request, HttpServletResponse response) {
        if (request.getAttribute(VERIFIED_KEY) instanceof String userId
                && request.getAttribute(ISSUED_AT_KEY) instanceof Instant issuedAt
                && issuedAt.plus(REFRESH_AFTER).isBefore(tokens.now())) {
            writeCookie(response, tokens.issue(userId), tokens.ttl());
        }
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
