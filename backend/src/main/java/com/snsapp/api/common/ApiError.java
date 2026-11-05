package com.snsapp.api.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ApiError(Instant timestamp, @Schema(description = "HTTP ステータス") int status,
                       @Schema(description = "画面にそのまま出せる日本語の説明") String message) {
    public static ApiError of(int status, String message) {
        return new ApiError(Instant.now(), status, message);
    }
}
