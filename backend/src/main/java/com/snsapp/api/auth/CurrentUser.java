package com.snsapp.api.auth;

import com.snsapp.api.common.ApiException;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * ログイン中の人を、Cookie に入ったアクセストークン(JWT、15分)で確かめる。
 * 切れたら、もう1つの Cookie のリフレッシュトークン(7日)と引き換えに作り直す(refresh)。
 * Cookie はどちらも HttpOnly(JavaScript から読めない)。AWS(https)では Secure も付ける。
 * 署名と期限に加えて、DB の「JWT の世代」と「ログアウトしたログイン」を見るので、期限前でも無効にできる
 */
@Component
public class CurrentUser {

    public static final String COOKIE_NAME = "SNS_TOKEN";
    public static final String REFRESH_COOKIE_NAME = "SNS_REFRESH";
    // リフレッシュトークンは、作り直し(/api/auth/refresh)とログアウト(/api/auth/logout)にだけ送らせる
    static final String REFRESH_COOKIE_PATH = "/api/auth";
    // 1つのリクエストの中で確かめ終わった人。RequestLogFilter がログに出す
    public static final String VERIFIED_KEY = CurrentUser.class.getName() + ".verified";
    private static final String EXPIRED = "ログインの有効期限が切れました。もう一度ログインしてください";

    private final TokenService tokens;
    private final RefreshTokens refreshTokens;
    private final UserRepository users;
    private final RevokedSessionRepository revokedSessions;
    private final boolean secureCookie;

    public CurrentUser(TokenService tokens, RefreshTokens refreshTokens, UserRepository users,
                       RevokedSessionRepository revokedSessions,
                       @Value("${app.auth.cookie-secure}") boolean secureCookie) {
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.revokedSessions = revokedSessions;
        this.secureCookie = secureCookie;
    }

    public String require(HttpServletRequest request) {
        if (request.getAttribute(VERIFIED_KEY) instanceof String verified) {
            return verified;
        }
        String token = readCookie(request, COOKIE_NAME);
        if (token == null) {
            throw ApiException.unauthorized("ログインが必要です");
        }
        TokenService.Verified verified = tokens.verify(token)
                .filter(this::stillValid)
                .orElseThrow(() -> ApiException.unauthorized(EXPIRED));
        request.setAttribute(VERIFIED_KEY, verified.userId());
        return verified.userId();
    }

    /**
     * 世代が今の値と同じで、ログアウトしていないログインか。
     * アカウントを消した人の JWT も、ユーザーが見つからないので断る
     */
    private boolean stillValid(TokenService.Verified verified) {
        boolean currentVersion = users.findById(verified.userId())
                .map(user -> user.getTokenVersion() == verified.version())
                .orElse(false);
        return currentVersion && !revokedSessions.existsById(verified.sessionId());
    }

    /** ログインした人に、新しいログインのアクセストークンとリフレッシュトークンを Cookie で渡す */
    public void signIn(HttpServletResponse response, User user) {
        String sessionId = TokenService.newSessionId();
        writeAccessCookie(response, tokens.issue(user.getId(), sessionId, user.getTokenVersion()));
        writeRefreshCookie(response, refreshTokens.issue(user, sessionId), refreshTokens.ttl());
    }

    /**
     * リフレッシュトークンを新しいものと取り替え、アクセストークンも作り直す。
     * 少し前にほかのタブが取り替えたときは、何もしない(Cookie はもう新しい)。
     * 使えないときは Cookie を消して 401
     */
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request, REFRESH_COOKIE_NAME);
        if (token == null) {
            throw ApiException.unauthorized("ログインが必要です");
        }
        RefreshTokens.Result result = refreshTokens.rotate(token);
        if (result.rotated().isPresent()) {
            RefreshTokens.Rotated rotated = result.rotated().get();
            writeAccessCookie(response, tokens.issue(rotated.userId(), rotated.sessionId(), rotated.version()));
            writeRefreshCookie(response, rotated.token(), refreshTokens.ttl());
        } else if (result.failure() == RefreshTokens.Failure.INVALID) {
            clearCookie(response);
            throw ApiException.unauthorized(EXPIRED);
        }
    }

    /**
     * この端末のログインを終える。そのログインの ID を記録してアクセストークンを期限前でも使えなくし、
     * リフレッシュトークンも消す。ブラウザの Cookie も消す。ほかの端末のログインはそのまま。
     * アクセストークンが切れていても、リフレッシュトークンからログインを特定して終える
     */
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        Optional.ofNullable(readCookie(request, COOKIE_NAME))
                .flatMap(tokens::verify)
                .map(TokenService.Verified::sessionId)
                .or(() -> Optional.ofNullable(readCookie(request, REFRESH_COOKIE_NAME)).flatMap(refreshTokens::sessionOf))
                .ifPresent(refreshTokens::revokeSession);
        clearCookie(response);
    }

    /** ブラウザの Cookie を2つとも消す(期限0の空の Cookie を返す) */
    public void clearCookie(HttpServletResponse response) {
        writeCookie(response, COOKIE_NAME, "", "/", "Lax", Duration.ZERO);
        writeRefreshCookie(response, "", Duration.ZERO);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeAccessCookie(HttpServletResponse response, String value) {
        writeCookie(response, COOKIE_NAME, value, "/", "Lax", tokens.ttl());
    }

    // 画面から fetch で送るだけなので Strict にする(ほかのサイトから来たときは送らない)
    private void writeRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        writeCookie(response, REFRESH_COOKIE_NAME, value, REFRESH_COOKIE_PATH, "Strict", maxAge);
    }

    private void writeCookie(HttpServletResponse response, String name, String value, String path, String sameSite,
                             Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
