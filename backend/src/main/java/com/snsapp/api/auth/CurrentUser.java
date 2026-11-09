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
 * ログイン中の人を、Cookie に入った JWT で確かめる。
 * Cookie は HttpOnly(JavaScript から読めない)・SameSite=Lax。AWS(https)では Secure も付ける。
 * 署名と期限に加えて、DB の「JWT の世代」と「ログアウトしたログイン」を見るので、期限前でも無効にできる
 */
@Component
public class CurrentUser {

    public static final String COOKIE_NAME = "SNS_TOKEN";
    // 1つのリクエストの中で確かめ終わった人。RequestLogFilter がログに出す
    public static final String VERIFIED_KEY = CurrentUser.class.getName() + ".verified";
    private static final String TOKEN_KEY = CurrentUser.class.getName() + ".token";
    // 作ってからこれより経った JWT は、操作のたびに作り直して期限を延ばす(毎回だと、すべての応答に Cookie が付く)
    static final Duration REFRESH_AFTER = Duration.ofMinutes(1);
    private static final String EXPIRED = "ログインの有効期限が切れました。もう一度ログインしてください";

    private final TokenService tokens;
    private final UserRepository users;
    private final RevokedSessionRepository revokedSessions;
    private final boolean secureCookie;

    public CurrentUser(TokenService tokens, UserRepository users, RevokedSessionRepository revokedSessions,
                       @Value("${app.auth.cookie-secure}") boolean secureCookie) {
        this.tokens = tokens;
        this.users = users;
        this.revokedSessions = revokedSessions;
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
                .filter(this::stillValid)
                .orElseThrow(() -> ApiException.unauthorized(EXPIRED));
        request.setAttribute(VERIFIED_KEY, verified.userId());
        request.setAttribute(TOKEN_KEY, verified);
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

    /** ログインした人に、新しいログインの JWT を Cookie で渡す */
    public void signIn(HttpServletResponse response, User user) {
        writeCookie(response, tokens.issue(user.getId(), TokenService.newSessionId(), user.getTokenVersion()), tokens.ttl());
    }

    /**
     * この端末のログインを終える。JWT が有効なら、そのログインの ID を記録して期限前でも使えなくし、
     * ブラウザの Cookie も消す。ほかの端末のログインはそのまま
     */
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        Optional.ofNullable(readCookie(request)).flatMap(tokens::verify).ifPresent(verified -> {
            revokedSessions.deleteExpired(tokens.now());
            // 作り直した JWT も同じ ID なので、最後に作り直した時刻から ttl たてば、どれも期限切れになる
            revokedSessions.save(new RevokedSession(verified.sessionId(), tokens.now().plus(tokens.ttl())));
        });
        clearCookie(response);
    }

    /** ブラウザの Cookie を消す(期限0の空の Cookie を返す) */
    public void clearCookie(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    /** require() で確かめた JWT が古くなっていれば、同じログインのまま作り直して期限を延ばす(LoginInterceptor が呼ぶ) */
    void refreshIfOld(HttpServletRequest request, HttpServletResponse response) {
        if (request.getAttribute(TOKEN_KEY) instanceof TokenService.Verified verified
                && verified.issuedAt().plus(REFRESH_AFTER).isBefore(tokens.now())) {
            writeCookie(response, tokens.issue(verified.userId(), verified.sessionId(), verified.version()), tokens.ttl());
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
