package com.itlk.myclaudecode.notification.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ADMIN_TOKEN_PREFIX = "auth:admin:token:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, "未登录，请先登录管理后台");
            return false;
        }

        String token = authHeader.substring(7);
        String adminFlag = stringRedisTemplate.opsForValue().get(ADMIN_TOKEN_PREFIX + token);
        if (adminFlag == null) {
            writeError(response, "登录已过期，请重新登录");
            return false;
        }

        // 刷新 TTL
        stringRedisTemplate.expire(ADMIN_TOKEN_PREFIX + token, 2, java.util.concurrent.TimeUnit.HOURS);
        request.setAttribute("adminToken", token);
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
