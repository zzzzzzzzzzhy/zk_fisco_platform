package com.wereen.competitionplatform.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.UserKycMapper;
import com.wereen.competitionplatform.model.entity.UserKyc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户KYC认证服务（简化版）
 *
 * 功能：
 * 1. 用户提交KYC信息（不上传身份证照片，只填写身份证号）
 * 2. 自动审核（基本校验）
 * 3. 人工审核（异常情况）
 * 4. 查询KYC状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserKycService {

    private final UserKycMapper userKycMapper;

    /**
     * 提交KYC认证
     *
     * @param userId 用户ID
     * @param realName 真实姓名
     * @param idCardNumber 身份证号（明文，方法内加密）
     * @param mobilePhone 手机号（明文，方法内加密）
     * @param bankCardNumber 银行卡号（明文，方法内加密）
     * @param bankName 开户行
     * @param bankBranch 开户支行
     * @return KYC记录
     */
    @Transactional(rollbackFor = Exception.class)
    public UserKyc submitKyc(Long userId, String realName, String idCardNumber,
                             String mobilePhone, String bankCardNumber,
                             String bankName, String bankBranch) {
        // 检查是否已提交过KYC
        UserKyc existingKyc = getUserKyc(userId);
        if (existingKyc != null) {
            if ("APPROVED".equals(existingKyc.getStatus())) {
                throw new BusinessException("您已完成KYC认证，无需重复提交");
            } else if ("PENDING".equals(existingKyc.getStatus())) {
                throw new BusinessException("您的KYC正在审核中，请耐心等待");
            }
            // 如果是REJECTED状态，允许重新提交
        }

        // 校验身份证号格式
        if (!isValidIdCard(idCardNumber)) {
            throw new BusinessException("身份证号格式不正确");
        }

        // 校验手机号格式
        if (!isValidMobile(mobilePhone)) {
            throw new BusinessException("手机号格式不正确");
        }

        // 校验银行卡号格式
        if (!isValidBankCard(bankCardNumber)) {
            throw new BusinessException("银行卡号格式不正确");
        }

        // 创建KYC记录
        UserKyc kyc = new UserKyc();
        kyc.setUserId(userId);
        kyc.setRealName(realName);

        // 加密敏感信息（简单加密示例，实际应使用更安全的加密方式）
        kyc.setIdCardNumber(encrypt(idCardNumber));
        kyc.setIdCardHash(hash(idCardNumber));

        kyc.setMobilePhone(encrypt(mobilePhone));
        kyc.setMobileHash(hash(mobilePhone));

        kyc.setBankCardNumber(encrypt(bankCardNumber));
        kyc.setBankCardHash(hash(bankCardNumber));

        kyc.setBankName(bankName);
        kyc.setBankBranch(bankBranch);

        kyc.setStatus("PENDING"); // 待审核
        kyc.setRetryCount(existingKyc != null ? existingKyc.getRetryCount() + 1 : 0);
        kyc.setDeleted(0);

        // TODO: 对接第三方KYC服务（阿里云实人认证等）
        // String thirdPartyResult = callThirdPartyKyc(realName, idCardNumber, mobilePhone);
        // kyc.setThirdPartyResult(thirdPartyResult);

        userKycMapper.insert(kyc);

        log.info("用户 {} 提交KYC认证，身份证号: {}", userId, maskIdCard(idCardNumber));

        // 自动审核
        autoReview(kyc.getId());

        return kyc;
    }

    /**
     * 自动审核KYC
     *
     * 当前版本：简单校验通过即自动通过
     * 后期扩展：对接第三方KYC服务进行实人认证
     *
     * @param kycId KYC记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoReview(Long kycId) {
        UserKyc kyc = userKycMapper.selectById(kycId);
        if (kyc == null) {
            throw new BusinessException("KYC记录不存在");
        }

        // TODO: 后期扩展 - 调用第三方KYC服务
        // 1. 运营商三要素验证（姓名+身份证号+手机号）
        // 2. 银行卡四要素验证（姓名+身份证号+银行卡号+手机号）
        // 3. 人脸活体检测（预留）

        // 当前版本：简单自动通过
        kyc.setStatus("APPROVED");
        kyc.setReviewedAt(LocalDateTime.now());
        userKycMapper.updateById(kyc);

        log.info("KYC自动审核通过: kycId={}, userId={}", kycId, kyc.getUserId());
    }

    /**
     * 人工审核KYC
     *
     * @param kycId KYC记录ID
     * @param reviewerId 审核员ID
     * @param approved 是否通过
     * @param rejectReason 拒绝原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualReview(Long kycId, Long reviewerId, boolean approved, String rejectReason) {
        UserKyc kyc = userKycMapper.selectById(kycId);
        if (kyc == null) {
            throw new BusinessException("KYC记录不存在");
        }

        if ("APPROVED".equals(kyc.getStatus())) {
            throw new BusinessException("该KYC已审核通过，无需重复审核");
        }

        kyc.setReviewerId(reviewerId);
        kyc.setReviewedAt(LocalDateTime.now());

        if (approved) {
            kyc.setStatus("APPROVED");
            kyc.setRejectReason(null);
            log.info("KYC人工审核通过: kycId={}, reviewerId={}", kycId, reviewerId);
        } else {
            kyc.setStatus("REJECTED");
            kyc.setRejectReason(rejectReason);
            log.info("KYC人工审核拒绝: kycId={}, reviewerId={}, reason={}", kycId, reviewerId, rejectReason);
        }

        userKycMapper.updateById(kyc);
    }

    /**
     * 获取用户KYC记录
     *
     * @param userId 用户ID
     * @return KYC记录
     */
    public UserKyc getUserKyc(Long userId) {
        LambdaQueryWrapper<UserKyc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserKyc::getUserId, userId)
                .eq(UserKyc::getDeleted, 0)
                .orderByDesc(UserKyc::getCreatedAt)
                .last("LIMIT 1");

        return userKycMapper.selectOne(wrapper);
    }

    /**
     * 检查用户是否已通过KYC
     *
     * @param userId 用户ID
     * @return true-已通过 false-未通过
     */
    public boolean isKycApproved(Long userId) {
        UserKyc kyc = getUserKyc(userId);
        return kyc != null && "APPROVED".equals(kyc.getStatus());
    }

    // ==================== 私有方法 ====================

    /**
     * 校验身份证号格式（简单校验）
     */
    private boolean isValidIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return false;
        }
        // 简单正则校验
        return idCard.matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");
    }

    /**
     * 校验手机号格式
     */
    private boolean isValidMobile(String mobile) {
        if (mobile == null || mobile.length() != 11) {
            return false;
        }
        return mobile.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 校验银行卡号格式（简单校验）
     */
    private boolean isValidBankCard(String bankCard) {
        if (bankCard == null) {
            return false;
        }
        int length = bankCard.length();
        return length >= 16 && length <= 19 && bankCard.matches("^\\d+$");
    }

    /**
     * 加密（简单示例，实际应使用AES等算法）
     */
    private String encrypt(String text) {
        // TODO: 使用AES加密
        // 当前使用Base64简单编码示例
        return java.util.Base64.getEncoder().encodeToString(text.getBytes());
    }

    /**
     * 哈希
     */
    private String hash(String text) {
        return SecureUtil.sha256(text);
    }

    /**
     * 脱敏身份证号
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return "****";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }
}
