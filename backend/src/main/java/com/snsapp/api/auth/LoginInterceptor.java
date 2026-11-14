package com.snsapp.api.auth;

import com.snsapp.api.common.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** @PublicApi の付いていない API は、入力の確認より先にログインを確かめる */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final CurrentUser currentUser;

    public LoginInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && !method.hasMethodAnnotation(PublicApi.class)) {
            currentUser.require(request);
        }
        return true;
    }
}
