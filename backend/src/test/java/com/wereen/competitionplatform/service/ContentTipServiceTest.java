package com.wereen.competitionplatform.service;

import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.ContentTipMapper;
import com.wereen.competitionplatform.mapper.UserMapper;
import com.wereen.competitionplatform.mapper.UserWalletMapper;
import com.wereen.competitionplatform.model.entity.User;
import com.wereen.competitionplatform.model.entity.UserWallet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 对 ContentTipService 的纯单元测试：
 * - 不启动 Spring 容器
 * - 不依赖数据库 / FISCO / 任何外部系统
 * - 只验证核心业务校验逻辑是否健壮
 */
@ExtendWith(MockitoExtension.class)
public class ContentTipServiceTest {

    @Mock
    private ForumTokenService forumTokenService;

    @Mock
    private UserWalletMapper userWalletMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ContentTipMapper contentTipMapper;

    @InjectMocks
    private ContentTipService contentTipService;

    @Test
    @DisplayName("不能给自己打赏")
    void cannotTipSelf() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> contentTipService.createTip(
                        1L, // tipperId
                        1L, // creatorId 相同
                        "CONTENT_SHARE",
                        100L,
                        new BigDecimal("10"),
                        "0xHASH",
                        123L
                )
        );
        assertEquals("不能给自己打赏", ex.getMessage());
    }

    @Test
    @DisplayName("打赏金额必须大于 0")
    void amountMustBePositive() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> contentTipService.createTip(
                        1L,
                        2L,
                        "CONTENT_SHARE",
                        100L,
                        BigDecimal.ZERO,
                        "0xHASH",
                        123L
                )
        );
        assertEquals("打赏金额必须大于0", ex.getMessage());
    }

    @Test
    @DisplayName("交易哈希不能为空")
    void txHashMustNotBeBlank() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> contentTipService.createTip(
                        1L,
                        2L,
                        "CONTENT_SHARE",
                        100L,
                        new BigDecimal("10"),
                        "  ", // 空白哈希
                        123L
                )
        );
        assertEquals("交易哈希不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("打赏者余额不足时应拒绝")
    void insufficientBalanceShouldFail() {
        // 模拟链上余额只有 5 WEE，但打赏 10
        when(forumTokenService.getUserTokenBalance(anyLong()))
                .thenReturn(new BigDecimal("5"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> contentTipService.createTip(
                        1L,
                        2L,
                        "CONTENT_SHARE",
                        100L,
                        new BigDecimal("10"),
                        "0xHASH",
                        123L
                )
        );
        assertEquals("余额不足", ex.getMessage());
    }

    @Test
    @DisplayName("未绑定钱包地址时应提示先绑定钱包")
    void missingWalletAddressShouldFail() {
        // forumTokenService 返回足够余额，让流程继续往下走到 requireWallet
        when(forumTokenService.getUserTokenBalance(anyLong()))
                .thenReturn(new BigDecimal("100"));

        // userWalletMapper 返回 null，模拟还没有钱包记录
        when(userWalletMapper.selectByUserId(1L)).thenReturn(null);

        // userMapper 返回一个没有绑定地址的用户
        User user = new User();
        user.setId(1L);
        user.setWalletAddress(null);
        when(userMapper.selectById(1L)).thenReturn(user);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> contentTipService.createTip(
                        1L,
                        2L,
                        "CONTENT_SHARE",
                        100L,
                        new BigDecimal("10"),
                        "0xHASH",
                        123L
                )
        );
        assertEquals("请先绑定钱包地址", ex.getMessage());
    }

    @Test
    @DisplayName("创作者未绑定钱包地址时也应提示先绑定")
    void creatorMissingWalletAddressShouldFail() {
        when(forumTokenService.getUserTokenBalance(1L))
                .thenReturn(new BigDecimal("100"));

        // 打赏者已有钱包记录且有地址
        UserWallet tipperWallet = new UserWallet();
        tipperWallet.setId(10L);
        tipperWallet.setUserId(1L);
        tipperWallet.setWalletAddress("0xTIPPER");
        when(userWalletMapper.selectByUserId(1L)).thenReturn(tipperWallet);

        // 创作者没有钱包记录且用户表里也没地址
        when(userWalletMapper.selectByUserId(2L)).thenReturn(null);
        User creator = new User();
        creator.setId(2L);
        creator.setWalletAddress(null);
        when(userMapper.selectById(2L)).thenReturn(creator);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> contentTipService.createTip(
                        1L,
                        2L,
                        "CONTENT_SHARE",
                        100L,
                        new BigDecimal("10"),
                        "0xHASH",
                        123L
                )
        );
        assertEquals("请先绑定钱包地址", ex.getMessage());
    }
}


