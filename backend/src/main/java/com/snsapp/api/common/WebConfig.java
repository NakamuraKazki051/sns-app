package com.snsapp.api.common;

import com.snsapp.api.auth.LoginInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // ブラウザから見たオリジンがアプリから見たものと違う環境のオリジン。カンマ区切りで完全一致のみ
    // AWS は CloudFront が https で受けてアプリへ http で渡すので、ここに入れないと POST が 403 になる
    // 例: APP_CORS_ALLOWED_ORIGINS=https://d111111abcdef8.cloudfront.net
    @Value("${app.cors.allowed-origins:}")
    private String extraAllowedOrigins;

    private final LoginInterceptor loginInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Cookie付きのリクエストを許可するため、任意のサイトではなくローカル開発用のオリジンに限定する
        // (「*」だと、ログイン中に開いた外部サイトから投稿できてしまう)
        List<String> origins = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
        Arrays.stream(extraAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty() && !origin.contains("*"))
                .forEach(origins::add);

        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowCredentials(true);
    }
}
