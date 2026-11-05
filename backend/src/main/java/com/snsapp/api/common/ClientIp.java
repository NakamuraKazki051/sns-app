package com.snsapp.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * リクエストを送った人の IP アドレス。
 * AWS ではアプリに直接届くのは CloudFront の IP なので、X-Forwarded-For を見る。
 * 利用者は前の部分を自由に書けるが、最後の1つは CloudFront が足した本当の IP。
 * ヘッダーを信じてよいのは OriginSecretFilter が CloudFront を通ったと確かめているときだけなので、ローカルでは見ない
 */
@Component
public class ClientIp {

    private final boolean behindCloudFront;

    public ClientIp(@Value("${app.origin-secret:}") String originSecret) {
        this.behindCloudFront = !originSecret.isEmpty();
    }

    public String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (behindCloudFront && forwarded != null && !forwarded.isBlank()) {
            return forwarded.substring(forwarded.lastIndexOf(',') + 1).strip();
        }
        return request.getRemoteAddr();
    }
}
