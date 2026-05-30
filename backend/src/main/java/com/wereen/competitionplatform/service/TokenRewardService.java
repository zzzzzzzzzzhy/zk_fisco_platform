package com.wereen.competitionplatform.service;

import com.wereen.competitionplatform.mapper.TokenRewardMapper;
import com.wereen.competitionplatform.model.entity.TokenReward;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 代币账单记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRewardService {

    private static final BigDecimal DECIMAL = BigDecimal.TEN.pow(18);

    private final TokenRewardMapper tokenRewardMapper;

    /**
     * 记录置顶消耗（支出）
     */
    public void recordPinConsume(Long userId, String contentType, String contentId,
                                BigDecimal amount, String txHash) {
        try {
            TokenReward reward = new TokenReward();
            reward.setUserId(userId);
            // 内容ID前面带上类型，方便前端区分：PIN:CONTENT_SHARE:123
            reward.setContentId("PIN:" + contentType + ":" + contentId);
            reward.setRewardType("PIN_CONSUME");
            // 存成最小单位，注意是支出，所以用负数
            BigInteger value = amount.multiply(DECIMAL).toBigInteger().negate();
            reward.setAmount(value);
            reward.setTxHash(txHash);
            tokenRewardMapper.insert(reward);
        } catch (Exception e) {
            // 账单记录失败不影响主流程
            log.error("记录置顶消耗账单失败: userId={}, contentType={}, contentId={}",
                userId, contentType, contentId, e);
        }
    }
}


