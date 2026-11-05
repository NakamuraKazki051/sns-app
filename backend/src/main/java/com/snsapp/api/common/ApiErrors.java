package com.snsapp.api.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 仕様書に載せる、この API が返すエラーの HTTP ステータス。
 * エラーは ApiException で返すので、ライブラリには読み取れない。ログインが要る API の 401 は書かなくても OpenApiConfig が足す
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {
    int[] value();
}
