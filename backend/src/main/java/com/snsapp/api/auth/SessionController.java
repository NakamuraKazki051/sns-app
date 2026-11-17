package com.snsapp.api.auth;

import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** 設定画面の「ログイン中の端末」。どの端末でログインしているかを見て、選んだ端末をログアウトさせる */
@RestController
@RequestMapping("/api/me/sessions")
@Tag(name = OpenApiConfig.TAG_ME)
public class SessionController {

    /**
     * ログイン1回分。device はブラウザと OS(「Chrome(Windows)」)。lastUsedAt はアクセストークンを最後に作り直した時刻
     * (使っている間は15分ごとに進む)。current はこの API を呼んだ端末
     */
    public record SessionResponse(String id, String device, Instant createdAt, Instant lastUsedAt, boolean current) {
    }

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final TokenService tokens;

    public SessionController(CurrentUser currentUser, UserRepository users, UserSessionRepository sessions,
                             TokenService tokens) {
        this.currentUser = currentUser;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
    }

    @Operation(operationId = "listSessions", summary = "ログイン中の端末",
            description = "今もログインしている端末を、最後に使った新しい順に返す。パスワードを変える前のログインと、7日使っていないものは出さない")
    @GetMapping
    public List<SessionResponse> list(HttpServletRequest request) {
        String sessionId = currentUser.requireSessionId(request);
        return active(currentUser.require(request)).stream()
                .map(s -> new SessionResponse(s.getId(), UserAgents.describe(s.getUserAgent()), s.getCreatedAt(),
                        s.getLastUsedAt(), s.getId().equals(sessionId)))
                .toList();
    }

    @Operation(operationId = "revokeSession", summary = "選んだ端末をログアウトさせる",
            description = "その端末のアクセストークンもリフレッシュトークンもすぐ使えなくなる。この端末を選んだら、ふつうのログアウトと同じ(Cookie も消す)")
    @ApiErrors(404)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        String sessionId = currentUser.requireSessionId(request);
        String userId = currentUser.require(request);
        // ほかの人のログイン・もう終わったログインは、無いものとして扱う
        UserSession session = sessions.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("ログインが見つかりません"));
        currentUser.revoke(session.getId());
        if (session.getId().equals(sessionId)) {
            currentUser.clearCookie(response);
        }
    }

    @Operation(operationId = "revokeOtherSessions", summary = "ほかの端末をすべてログアウトさせる",
            description = "この端末だけログインしたままにする")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(HttpServletRequest request) {
        String sessionId = currentUser.requireSessionId(request);
        for (UserSession session : active(currentUser.require(request))) {
            if (!session.getId().equals(sessionId)) {
                currentUser.revoke(session.getId());
            }
        }
    }

    private List<UserSession> active(String userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
        return sessions.findActive(user.getId(), user.getTokenVersion(), tokens.now());
    }
}
