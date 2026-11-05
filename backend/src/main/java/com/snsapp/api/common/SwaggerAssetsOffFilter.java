package com.snsapp.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** API 仕様書を切っていても、Swagger UI の画面のファイルは /webjars/ から読めてしまうので塞ぐ */
@Component
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "false")
public class SwaggerAssetsOffFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/webjars/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
    }
}
