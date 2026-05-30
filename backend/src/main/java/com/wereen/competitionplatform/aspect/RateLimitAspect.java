package com.wereen.competitionplatform.aspect;

import com.wereen.competitionplatform.annotation.RateLimit;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 使用 Redis 实现分布式限流
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    @Before("@annotation(com.wereen.competitionplatform.annotation.RateLimit)")
    public void doBefore(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit != null) {
            // 构造限流key
            String key = buildKey(rateLimit);

            // 获取当前请求次数
            String countStr = redisTemplate.opsForValue().get(key);
            Integer count = countStr == null ? 0 : Integer.parseInt(countStr);

            if (count >= rateLimit.count()) {
                log.warn("限流触发 - key: {}, 当前请求次数: {}, 限制次数: {}", key, count, rateLimit.count());
                throw new BusinessException(String.format("访问过于频繁，请在 %d 秒后再试", rateLimit.time()));
            }

            // 增加请求次数
            if (count == 0) {
                // 首次请求，设置过期时间
                redisTemplate.opsForValue().set(key, "1", rateLimit.time(), TimeUnit.SECONDS);
            } else {
                // 递增请求次数
                redisTemplate.opsForValue().increment(key);
            }

            log.debug("限流检查通过 - key: {}, 当前请求次数: {}/{}", key, count + 1, rateLimit.count());
        }
    }

    /**
     * 构造限流key
     */
    private String buildKey(RateLimit rateLimit) {
        StringBuilder key = new StringBuilder(rateLimit.key());
        key.append(":");

        switch (rateLimit.limitType()) {
            case IP:
                key.append(getIpAddress());
                break;
            case USER:
                key.append(getUserId());
                break;
            case GLOBAL:
                key.append("global");
                break;
        }

        return key.toString();
    }

    /**
     * 获取客户端IP地址
     */
    private String getIpAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 获取用户ID
     */
    private String getUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "anonymous";
            }

            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                String jwtToken = token.substring(7);
                Long userId = jwtUtil.getUserIdFromToken(jwtToken);
                return userId != null ? userId.toString() : "anonymous";
            }
            return "anonymous";
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
            return "anonymous";
        }
    }
}
