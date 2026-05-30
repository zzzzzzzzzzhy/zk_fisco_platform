package com.wereen.competitionplatform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务
 * 
 * @author Wereen
 * @date 2025-12-03
 */
@Slf4j
@Service
public class CaptchaService {

    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;

    public CaptchaService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成验证码令牌
     * 
     * @param userIdentifier 用户标识（IP、用户ID等）
     * @return 验证码令牌
     */
    public String generateCaptchaToken(String userIdentifier) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = CAPTCHA_PREFIX + token;
        
        // 存储到 Redis，5 分钟过期
        redisTemplate.opsForValue().set(key, userIdentifier, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        log.info("生成验证码令牌: {} for {}", token, userIdentifier);
        return token;
    }

    /**
     * 验证验证码令牌
     * 
     * @param token 验证码令牌
     * @param userIdentifier 用户标识
     * @return 是否验证通过
     */
    public boolean verifyCaptchaToken(String token, String userIdentifier) {
        if (token == null || token.isEmpty()) {
            log.warn("验证码令牌为空");
            return false;
        }

        String key = CAPTCHA_PREFIX + token;
        String storedIdentifier = redisTemplate.opsForValue().get(key);
        
        if (storedIdentifier == null) {
            log.warn("验证码令牌不存在或已过期: {}", token);
            return false;
        }

        if (!storedIdentifier.equals(userIdentifier)) {
            log.warn("验证码令牌不匹配: expected={}, actual={}", storedIdentifier, userIdentifier);
            return false;
        }

        // 验证成功后删除令牌（一次性使用）
        redisTemplate.delete(key);
        log.info("验证码验证成功: {} for {}", token, userIdentifier);
        
        return true;
    }

    /**
     * 验证验证码令牌（不删除，允许多次验证）
     * 
     * @param token 验证码令牌
     * @param userIdentifier 用户标识
     * @return 是否验证通过
     */
    public boolean verifyCaptchaTokenWithoutDelete(String token, String userIdentifier) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String key = CAPTCHA_PREFIX + token;
        String storedIdentifier = redisTemplate.opsForValue().get(key);
        
        return storedIdentifier != null && storedIdentifier.equals(userIdentifier);
    }

    /**
     * 删除验证码令牌
     * 
     * @param token 验证码令牌
     */
    public void deleteCaptchaToken(String token) {
        if (token != null && !token.isEmpty()) {
            String key = CAPTCHA_PREFIX + token;
            redisTemplate.delete(key);
        }
    }

    /**
     * 检查是否需要验证码（基于频率限制）
     * 
     * @param userIdentifier 用户标识
     * @param action 操作类型（如 "checkin", "post", "comment"）
     * @param maxAttempts 最大尝试次数
     * @param timeWindowMinutes 时间窗口（分钟）
     * @return 是否需要验证码
     */
    public boolean needsCaptcha(String userIdentifier, String action, int maxAttempts, int timeWindowMinutes) {
        String key = "rate_limit:" + action + ":" + userIdentifier;
        String countStr = redisTemplate.opsForValue().get(key);
        
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        
        if (count >= maxAttempts) {
            log.info("用户 {} 操作 {} 超过限制，需要验证码", userIdentifier, action);
            return true;
        }
        
        // 增加计数
        redisTemplate.opsForValue().set(key, String.valueOf(count + 1), timeWindowMinutes, TimeUnit.MINUTES);
        
        return false;
    }
}

