package com.itlk.myclaudecode.auth.interceptor;

import com.itlk.myclaudecode.auth.service.TokenManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Resource
    private TokenManager tokenManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, "未登录，请先登录");
            return false;
        }

        String token = authHeader.substring(7);
        Long userId = tokenManager.getUserId(token);
        if (userId == null) {
            writeError(response, "登录已过期，请重新登录");
            return false;
        }

        tokenManager.refreshToken(token, userId);
        request.setAttribute("userId", userId);
        request.setAttribute("token", token);
        return true;
    }

    private void writeError(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", 0, "msg", msg, "data", (Object) "")
        ));
    }
}
