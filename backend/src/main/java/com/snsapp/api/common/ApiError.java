package com.snsapp.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ApiError(Instant timestamp, @Schema(description = "HTTP ステータス") int status,
                       @Schema(description = "画面にそのまま出せる日本語の説明") String message,
                       @JsonInclude(JsonInclude.Include.NON_NULL)
                       @Schema(description = "画面が出し分けに使う目印。ログインで認証アプリのコードが要るときの TWO_FACTOR_REQUIRED だけ。ほかは付かない")
                       String code) {
    public static ApiError of(int status, String message) {
        return new ApiError(Instant.now(), status, message, null);
    }

    public static ApiError of(int status, String message, String code) {
        return new ApiError(Instant.now(), status, message, code);
    }
}
