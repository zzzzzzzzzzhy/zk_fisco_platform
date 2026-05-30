package com.wereen.competitionplatform.interceptor;

import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.model.entity.User;
import com.wereen.competitionplatform.util.JwtUtil;
import com.wereen.competitionplatform.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 角色权限拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 如果不是方法处理器，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);

        // 如果没有权限注解，直接放行
        if (requireRole == null) {
            return true;
        }

        // 获取token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new BusinessException("未登录或登录已过期");
        }

        token = token.substring(7);

        // 获取用户角色
        String userRole = resolveUserRole(token);
        if (userRole == null) {
            throw new BusinessException("用户角色信息缺失");
        }

        // 检查是否有所需角色
        String[] requiredRoles = requireRole.value();
        boolean hasRole = Arrays.asList(requiredRoles).contains(userRole);

        if (!hasRole) {
            log.warn("用户角色 {} 无权访问接口 {}", userRole, request.getRequestURI());
            throw new BusinessException("权限不足，需要角色: " + Arrays.toString(requiredRoles));
        }

        return true;
    }

    /**
     * 解析用户角色，若 Token 中缺失则回源数据库兜底
     */
    private String resolveUserRole(String token) {
        String userRole = jwtUtil.getRoleFromToken(token);
        if (userRole != null && !userRole.isEmpty()) {
            return userRole;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            log.warn("Token 中缺失角色且无法解析 userId");
            return null;
        }

        try {
            User user = userService.getUserById(userId);
            if (user == null) {
                log.warn("根据 userId={} 未查询到用户记录", userId);
                return null;
            }
            return user.getRole() != null ? user.getRole() : UserRole.USER;
        } catch (Exception e) {
            log.warn("回源查询用户角色失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }
}
