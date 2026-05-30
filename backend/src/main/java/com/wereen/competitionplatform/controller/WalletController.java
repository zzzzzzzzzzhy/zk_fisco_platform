package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.WalletBalance;
import com.wereen.competitionplatform.model.entity.WalletTransaction;
import com.wereen.competitionplatform.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 钱包控制器
 */
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

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
}
