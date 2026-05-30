package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.service.CaptchaService;
import com.wereen.competitionplatform.utils.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 验证码 API
 * 
 * @author Wereen
 * @date 2025-12-03
 */
@Slf4j
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    /**
     * 验证码验证成功后生成令牌
     */
    @PostMapping("/verify")
    public Result<Map<String, String>> verifyCaptcha(HttpServletRequest request) {
        try {
            // 获取用户 IP 作为标识
            String userIp = IpUtils.getClientIp(request);
            
            // 生成验证码令牌
            String token = captchaService.generateCaptchaToken(userIp);
            
            Map<String, String> result = new HashMap<>();
            result.put("token", token);
            result.put("expiresIn", "300"); // 5 分钟
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("验证码验证失败", e);
            return Result.error("验证失败，请重试");
        }
    }

    /**
     * 检查是否需要验证码
     */
    @GetMapping("/check")
    public Result<Map<String, Object>> checkNeedCaptcha(
            @RequestParam String action,
            HttpServletRequest request
    ) {
        try {
            String userIp = IpUtils.getClientIp(request);
            
            // 根据不同操作设置不同的限制
            int maxAttempts = getMaxAttempts(action);
            int timeWindow = getTimeWindow(action);
            
            boolean needsCaptcha = captchaService.needsCaptcha(userIp, action, maxAttempts, timeWindow);
            
            Map<String, Object> result = new HashMap<>();
            result.put("needsCaptcha", needsCaptcha);
            result.put("action", action);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("检查验证码需求失败", e);
            return Result.error("检查失败");
        }
    }

    /**
     * 根据操作类型获取最大尝试次数
     */
    private int getMaxAttempts(String action) {
        return switch (action) {
            case "checkin" -> 5;      // 签到：5次/小时
            case "post" -> 10;        // 发帖：10次/小时
            case "comment" -> 20;     // 评论：20次/小时
            case "pin" -> 3;          // 置顶：3次/小时
            default -> 10;
        };
    }

    /**
     * 根据操作类型获取时间窗口（分钟）
     */
    private int getTimeWindow(String action) {
        return switch (action) {
            case "checkin" -> 60;     // 1 小时
            case "post" -> 60;        // 1 小时
            case "comment" -> 30;     // 30 分钟
            case "pin" -> 60;         // 1 小时
            default -> 60;
        };
    }
}

