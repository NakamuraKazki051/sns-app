package com.snsapp.api.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** API 仕様書で「ログインが要らない」とする API。ログインの確認はコードの中でしているので、ライブラリには読み取れない */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
