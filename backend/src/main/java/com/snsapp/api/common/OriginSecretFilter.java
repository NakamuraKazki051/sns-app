package com.snsapp.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * AWS で、自分の CloudFront を通ったリクエストだけを受け付ける。
 * EC2 の80番は CloudFront 全体に開いているので、他人が作った CloudFront からも届く。
 * 自分の CloudFront だけが秘密のヘッダーを付けるので、これが無ければ断る。ローカルは app.origin-secret が空で、確かめない
 */
@Component
public class OriginSecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Origin-Verify";

    private final byte[] secret;

    public OriginSecretFilter(@Value("${app.origin-secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (secret.length > 0 && !matches(request.getHeader(HEADER))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String value) {
        return value != null && MessageDigest.isEqual(secret, value.getBytes(StandardCharsets.UTF_8));
    }
}
