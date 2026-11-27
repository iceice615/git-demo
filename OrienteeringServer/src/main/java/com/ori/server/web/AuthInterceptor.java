package com.ori.server.web;

import com.ori.server.entity.User;
import com.ori.server.security.TokenAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 统一鉴权拦截器：
 * - 白名单（注册/登录）放行
 * - 其余 /api/** 必须带 Authorization: Bearer <token>
 * - 通过后把用户 id 写入 request 属性，业务层直接取用，不再信任请求体里的 userId
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenAuth tokenAuth;

    public AuthInterceptor(TokenAuth tokenAuth) {
        this.tokenAuth = tokenAuth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = header.substring(7).trim();
        }
        User user = tokenAuth.resolve(token).orElse(null);
        if (user == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"ok\":false,\"msg\":\"登录已过期，请重新登录\"}");
            return false;
        }
        request.setAttribute(TokenAuth.ATTR_UID, user.id);
        request.setAttribute("currentUser", user);
        return true;
    }
}
