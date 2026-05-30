package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.WalletBalance;
import com.wereen.competitionplatform.model.entity.WalletTransaction;
import com.wereen.competitionplatform.service.WalletService;
import com.wereen.competitionplatform.service.WeeBalanceService;
import com.wereen.competitionplatform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 钱包控制器
 */
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WeeBalanceService weeBalanceService;
    private final JwtUtil jwtUtil;

    /**
     * 获取当前登录用户 WEE 余额（前端主要调此接口）
     */
    @GetMapping("/wee")
    public Result<Long> getMyWeeBalance(HttpServletRequest request) {
        String token = resolveToken(request);
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) return Result.error("未登录");
        return Result.success(weeBalanceService.getBalance(userId));
    }

    /**
     * 获取用户余额
     */
    @GetMapping("/balance")
    public Result<WalletBalance> getUserBalance(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "CNY") String currency) {

        WalletBalance balance = walletService.getUserBalance(userId, currency);
        return Result.success(balance);
    }

    /**
     * 获取交易流水
     */
    @GetMapping("/transactions")
    public Result<PageResult<WalletTransaction>> getTransactions(
            @RequestParam Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {

        PageResult<WalletTransaction> page = walletService.getTransactions(userId, type, current, size);
        return Result.success(page);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) return bearer.substring(7);
        return bearer;
    }
}
