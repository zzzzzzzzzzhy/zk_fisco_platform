package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.mapper.WithdrawRequestMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.model.entity.WalletBalance;
import com.wereen.competitionplatform.model.entity.WithdrawRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 提现服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final WithdrawRequestMapper withdrawRequestMapper;
    private final WalletService walletService;
    private final BlockchainService blockchainService;
    private final ChainProofMapper chainProofMapper;

    /**
     * 创建提现申请
     *
     * @param userId 用户ID
     * @param method 提现方式
     * @param accountPayload 收款账号（加密）
     * @param amount 提现金额
     * @param currency 币种
     * @return 提现申请记录
     */
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRequest createWithdrawRequest(Long userId, String method, String accountPayload,
                                                 Long amount, String currency) {
        // 1. 参数校验
        if (amount <= 0) {
            throw new BusinessException("提现金额必须大于0");
        }

        // 2. 检查余额
        WalletBalance balance = walletService.getUserBalance(userId, currency);
        if (balance.getBalance() < amount) {
            throw new BusinessException("余额不足");
        }

        // 3. 计算手续费和税费（简化处理，实际应根据规则计算）
        Long fee = calculateFee(amount);
        Long tax = calculateTax(amount);
        Long actualAmount = amount - fee - tax;

        if (actualAmount <= 0) {
            throw new BusinessException("扣除手续费和税费后金额不足");
        }

        // 4. 风控评分（简化处理，实际应接入风控系统）
        Integer riskScore = calculateRiskScore(userId, amount);

        // 5. 创建提现申请记录
        WithdrawRequest request = new WithdrawRequest();
        request.setUserId(userId);
        request.setMethod(method);
        request.setAccountPayload(accountPayload);
        request.setAmount(amount);
        request.setFee(fee);
        request.setTax(tax);
        request.setStatus("APPLIED"); // 已申请
        request.setRiskScore(riskScore);
        request.setChainStatus(0); // 未上链

        withdrawRequestMapper.insert(request);
        log.info("创建提现申请: requestId={}, userId={}, amount={}", request.getId(), userId, amount);

        // 6. 冻结余额
        try {
            walletService.freezeBalance(userId, currency, amount, "withdraw:" + request.getId());
        } catch (Exception e) {
            log.error("冻结余额失败: userId={}, amount={}", userId, amount, e);
            throw new BusinessException("冻结余额失败: " + e.getMessage());
        }

        // 7. 异步上链
        uploadWithdrawToBlockchainAsync(request.getId(), "APPLIED");

        return request;
    }

    /**
     * 审核提现申请
     *
     * @param requestId 申请ID
     * @param approved 是否通过
     * @param reviewerId 审核人ID
     * @param reason 审核原因（拒绝时必填）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewWithdrawRequest(Long requestId, Boolean approved, Long reviewerId, String reason) {
        WithdrawRequest request = getWithdrawRequestById(requestId);

        if (!"APPLIED".equals(request.getStatus())) {
            throw new BusinessException("提现申请状态不正确，无法审核");
        }

        if (approved) {
            // 审核通过
            request.setStatus("APPROVED");
            log.info("提现申请审核通过: requestId={}, reviewerId={}", requestId, reviewerId);

            // 上链记录审核通过状态
            uploadWithdrawToBlockchainAsync(requestId, "APPROVED");

        } else {
            // 审核拒绝
            request.setStatus("REJECTED");
            request.setFailureReason(reason);
            log.info("提现申请审核拒绝: requestId={}, reason={}", requestId, reason);

            // 解冻余额
            walletService.unfreezeBalance(
                request.getUserId(),
                "CNY",
                request.getAmount(),
                "withdraw_reject:" + requestId
            );

            // 上链记录审核拒绝状态
            uploadWithdrawToBlockchainAsync(requestId, "REJECTED");
        }

        withdrawRequestMapper.updateById(request);
    }

    /**
     * 标记提现已支付
     *
     * @param requestId 申请ID
     * @param provider 支付渠道
     * @param providerTxId 渠道交易ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markWithdrawPaid(Long requestId, String provider, String providerTxId) {
        WithdrawRequest request = getWithdrawRequestById(requestId);

        if (!"APPROVED".equals(request.getStatus())) {
            throw new BusinessException("提现申请状态不正确，无法标记为已支付");
        }

        // 更新状态
        request.setStatus("PAID");
        request.setProvider(provider);
        request.setProviderTxId(providerTxId);
        request.setPaidAt(LocalDateTime.now());
        withdrawRequestMapper.updateById(request);

        // 扣除冻结金额
        walletService.deductFrozenAmount(
            request.getUserId(),
            "CNY",
            request.getAmount(),
            "withdraw_paid:" + requestId
        );

        log.info("提现已支付: requestId={}, provider={}, providerTxId={}",
            requestId, provider, providerTxId);

        // 上链记录支付完成状态（关键操作）
        uploadWithdrawToBlockchainAsync(requestId, "PAID");
    }

    /**
     * 标记提现失败
     *
     * @param requestId 申请ID
     * @param failureReason 失败原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void markWithdrawFailed(Long requestId, String failureReason) {
        WithdrawRequest request = getWithdrawRequestById(requestId);

        if (!"APPROVED".equals(request.getStatus())) {
            throw new BusinessException("提现申请状态不正确");
        }

        // 更新状态
        request.setStatus("FAILED");
        request.setFailureReason(failureReason);
        withdrawRequestMapper.updateById(request);

        // 解冻余额
        walletService.unfreezeBalance(
            request.getUserId(),
            "CNY",
            request.getAmount(),
            "withdraw_failed:" + requestId
        );

        log.info("提现失败: requestId={}, reason={}", requestId, failureReason);

        // 上链记录失败状态
        uploadWithdrawToBlockchainAsync(requestId, "FAILED");
    }

    /**
     * 根据ID查询提现申请
     */
    public WithdrawRequest getWithdrawRequestById(Long id) {
        WithdrawRequest request = withdrawRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException("提现申请不存在");
        }
        return request;
    }

    /**
     * 查询用户的提现申请列表
     */
    public PageResult<WithdrawRequest> getUserWithdrawRequests(Long userId, String status,
                                                               Long current, Long size) {
        Page<WithdrawRequest> page = new Page<>(current, size);
        LambdaQueryWrapper<WithdrawRequest> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(WithdrawRequest::getUserId, userId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(WithdrawRequest::getStatus, status);
        }
        wrapper.orderByDesc(WithdrawRequest::getCreatedAt);

        Page<WithdrawRequest> resultPage = withdrawRequestMapper.selectPage(page, wrapper);

        return new PageResult<>(
            resultPage.getTotal(),
            resultPage.getCurrent(),
            resultPage.getSize(),
            resultPage.getRecords()
        );
    }

    /**
     * 异步上链处理
     *
     * @param requestId 提现申请ID
     * @param actionType 操作类型（APPLIED/APPROVED/REJECTED/PAID/FAILED）
     */
    @Async
    public void uploadWithdrawToBlockchainAsync(Long requestId, String actionType) {
        try {
            log.info("开始提现申请上链: requestId={}, actionType={}", requestId, actionType);

            WithdrawRequest request = getWithdrawRequestById(requestId);

            // 更新状态为上链中
            request.setChainStatus(1);
            withdrawRequestMapper.updateById(request);

            // 计算提现申请哈希（包含关键信息）
            String requestHash = calculateWithdrawHash(
                request.getId(),
                request.getUserId(),
                request.getAmount(),
                request.getMethod(),
                request.getStatus(),
                actionType
            );
            request.setRequestHash(requestHash);

            // 构造元数据（JSON格式）
            String metadata = String.format(
                "{\"requestId\":%d,\"userId\":%d,\"amount\":%d,\"method\":\"%s\",\"status\":\"%s\",\"actionType\":\"%s\",\"provider\":\"%s\",\"providerTxId\":\"%s\",\"timestamp\":\"%s\"}",
                request.getId(),
                request.getUserId(),
                request.getAmount(),
                request.getMethod(),
                request.getStatus(),
                actionType,
                request.getProvider() != null ? request.getProvider() : "",
                request.getProviderTxId() != null ? request.getProviderTxId() : "",
                LocalDateTime.now().toString()
            );

            // 调用区块链服务上链
            TransactionReceipt receipt = blockchainService.saveEvidence(
                "WITHDRAW",
                String.valueOf(request.getId()) + ":" + actionType, // bizId 包含操作类型，支持同一申请的多次上链
                requestHash
            );

            // 解析交易回执
            String txHash = receipt.getTransactionHash();
            Long blockHeight = Long.valueOf(receipt.getBlockNumber());
            LocalDateTime blockTime = LocalDateTime.now();

            // 保存链上存证记录
            ChainProof chainProof = new ChainProof();
            chainProof.setBizType("WITHDRAW");
            chainProof.setBizId(request.getId());
            chainProof.setDataHash(requestHash);
            chainProof.setTxHash(txHash);
            chainProof.setBlockHeight(blockHeight);
            chainProof.setBlockTime(blockTime);
            chainProof.setMetadata(metadata);
            chainProof.setStatus(2); // 已上链
            chainProofMapper.insert(chainProof);

            // 更新提现申请的链上状态（只保存最后一次上链的信息）
            request.setChainStatus(2); // 已上链
            request.setChainTxHash(txHash);
            request.setBlockHeight(blockHeight);
            request.setBlockTime(blockTime);
            withdrawRequestMapper.updateById(request);

            log.info("提现申请上链成功: requestId={}, actionType={}, txHash={}, blockHeight={}",
                requestId, actionType, txHash, blockHeight);

        } catch (Exception e) {
            log.error("提现申请上链失败: requestId={}, actionType={}", requestId, actionType, e);
            // 更新为上链失败状态
            try {
                WithdrawRequest request = getWithdrawRequestById(requestId);
                request.setChainStatus(3);
                withdrawRequestMapper.updateById(request);
            } catch (Exception ex) {
                log.error("更新上链失败状态异常", ex);
            }
        }
    }

    /**
     * 计算提现申请哈希
     */
    private String calculateWithdrawHash(Long requestId, Long userId, Long amount,
                                        String method, String status, String actionType) {
        try {
            // 拼接关键数据
            String data = String.format("%d|%d|%d|%s|%s|%s|%s",
                requestId, userId, amount, method, status, actionType,
                LocalDateTime.now().toString());

            // 计算 SHA-256 哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            log.error("计算提现申请哈希失败", e);
            throw new RuntimeException("哈希计算失败", e);
        }
    }

    /**
     * 计算手续费（简化实现）
     */
    private Long calculateFee(Long amount) {
        // 按2%计算手续费
        return (long) (amount * 0.02);
    }

    /**
     * 计算税费（简化实现）
     */
    private Long calculateTax(Long amount) {
        // 按3%计算税费
        return (long) (amount * 0.03);
    }

    /**
     * 计算风控评分（简化实现）
     */
    private Integer calculateRiskScore(Long userId, Long amount) {
        // TODO: 接入真实风控系统
        // 简化实现：根据金额大小返回风控评分
        if (amount > 1000000) {
            return 80; // 高风险
        } else if (amount > 100000) {
            return 50; // 中风险
        } else {
            return 20; // 低风险
        }
    }
}
