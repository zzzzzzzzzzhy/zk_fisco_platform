package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.service.ForumTokenService;
import com.wereen.competitionplatform.service.ForumTokenService.ForumRewardInfo;
import com.wereen.competitionplatform.service.ForumTokenService.TokenRewardView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 论坛代币接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/forum/token")
@Tag(name = "论坛代币管理", description = "论坛代币相关接口")
public class ForumTokenController {

    private final ForumTokenService forumTokenService;

    @Value("${blockchain.admin.private-key}")
    private String adminPrivateKey;

    @Value("${blockchain.forum-token.extension-address}")
    private String forumExtensionAddress;

    @GetMapping("/balance")
    @Operation(summary = "获取用户代币余额", description = "返回绑定钱包的 MTK 余额")
    public Result<BigDecimal> getUserTokenBalance() {
        Long userId = getCurrentUserId();
        return Result.success(forumTokenService.getUserTokenBalance(userId));
    }

    // 移除每日签到接口，改为前端直连合约方式
    // 签到功能现在完全通过前端Web3直接调用智能合约实现

    // 签到状态检查也改为前端直接调用合约
    // 这个接口保留作为备用，但主要状态检查通过Web3实现
    @GetMapping("/checkin-status")
    @Operation(summary = "获取签到状态信息", description = "返回用户的签到统计信息")
    public Result<ForumRewardInfo> getCheckinStatus() {
        Long userId = getCurrentUserId();
        return Result.success(forumTokenService.getUserRewardInfo(userId));
    }

    /**
     * 评论奖励签名（新模式：用户自行上链领取）
     * 前端在评论成功后调用该接口获取签名，然后调用合约 claimCommentReward 领取奖励。
     */
    @PostMapping("/comment/reward-sign")
    @Operation(summary = "评论奖励签名", description = "返回领取评论奖励所需的签名数据")
    public Result<Map<String, Object>> signCommentReward(@RequestBody Map<String, String> request) {
        String commentId = request.get("commentId");
        String userAddress = request.get("userAddress");
        try {
            if (commentId == null || commentId.isEmpty()) {
                return Result.error("评论ID不能为空");
            }
            if (userAddress == null || !userAddress.matches("^0x[a-fA-F0-9]{40}$")) {
                return Result.error("无效的用户地址");
            }

            userAddress = userAddress.toLowerCase();

            // 5分钟过期
            long deadline = Instant.now().getEpochSecond() + 300;

            // RewardType.COMMENT = 1
            String signature = signRewardMessage(
                userAddress,
                1,
                commentId,
                deadline
            );

            Map<String, Object> data = new HashMap<>();
            data.put("user", userAddress);
            data.put("commentId", commentId);
            data.put("deadline", deadline);
            data.put("signature", signature);

            return Result.success(data);
        } catch (Exception e) {
            log.error("生成评论奖励签名失败: commentId={}", commentId, e);
            return Result.error("生成评论奖励签名失败: " + e.getMessage());
        }
    }

    @PostMapping("/reward-post")
    @Operation(summary = "发帖奖励", description = "用户发布帖子后自动发放奖励")
    public Result<String> rewardPost(@RequestParam String postId) {
        Long userId = getCurrentUserId();
        try {
            String txHash = forumTokenService.rewardPost(userId, postId);
            return Result.success(txHash);
        } catch (Exception e) {
            log.error("发帖奖励发放失败: userId={}, postId={}, error={}", userId, postId, e.getMessage());
            return Result.success(null);
        }
    }

    @GetMapping("/reward-info")
    @Operation(summary = "获取奖励信息", description = "返回链上奖励统计信息")
    public Result<ForumRewardInfo> getRewardInfo() {
        Long userId = getCurrentUserId();
        return Result.success(forumTokenService.getUserRewardInfo(userId));
    }

    @GetMapping("/reward-history")
    @Operation(summary = "获取奖励记录", description = "分页返回真实奖励记录")
    public Result<PageResult<TokenRewardView>> getRewardHistory(
        @RequestParam(defaultValue = "1") Long current,
        @RequestParam(defaultValue = "10") Long size) {
        Long userId = getCurrentUserId();
        return Result.success(forumTokenService.getRewardHistory(userId, current, size));
    }

    @GetMapping("/usage-guide")
    @Operation(summary = "获取代币使用说明")
    public Result<TokenUsageGuide> getTokenUsageGuide() {
        TokenUsageGuide guide = TokenUsageGuide.builder()
            .earnWays(new String[]{
                "发布帖子 · 10 WEE",
                "发表评论 · 2 WEE",
                "内容分享（图片） · 5 WEE",
                "内容分享（视频） · 10 WEE",
                "每日签到 · 5 WEE + 连续奖励"
            })
            .useWays(new String[]{
                "购买竞赛资料",
                "竞赛报名抵扣",
                "帖子置顶/解锁高级功能"
            })
            .tokenSymbol("WEE")
            .network("Polygon Amoy")
            .contractAddress("0x3b90669eB9960d1e65D3A09097a9363Df74783DD")
            .build();
        return Result.success(guide);
    }

    private Long getCurrentUserId() {
        // 临时解决方案：从请求头获取用户ID
        // 在生产环境中应该使用Spring Security的认证机制
        try {
            jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                 org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();

            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                return Long.parseLong(userIdHeader);
            }

            // 开发环境回退方案
            String devUserId = System.getProperty("dev.userId", "1");
            return Long.parseLong(devUserId);
        } catch (Exception e) {
            log.warn("获取用户ID失败，使用默认用户ID: {}", e.getMessage());
            return 1L;
        }
    }

    /**
     * 按照合约 _hashRewardMessage 的格式签名奖励消息
     * msgHash = keccak256(abi.encodePacked("WEE_REWARD", address(this), block.chainid, user, rewardType, bizId, deadline))
     * ethHash = toEthSignedMessageHash(msgHash)
     */
    private String signRewardMessage(String userAddress, int rewardType, String bizId, long deadline) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            // "WEE_REWARD"
            buffer.put("WEE_REWARD".getBytes(StandardCharsets.UTF_8));

            // address(this) - 合约地址（20 bytes）
            buffer.put(Numeric.hexStringToByteArray(forumExtensionAddress));

            // chainid (Polygon = 137)
            buffer.put(toBytes32(BigInteger.valueOf(137)));

            // user 地址（20 bytes）
            buffer.put(Numeric.hexStringToByteArray(userAddress));

            // rewardType (uint8)
            buffer.put((byte) rewardType);

            // bizId (string)
            buffer.put(bizId.getBytes(StandardCharsets.UTF_8));

            // deadline (uint256)
            buffer.put(toBytes32(BigInteger.valueOf(deadline)));

            byte[] messageBytes = new byte[buffer.position()];
            buffer.flip();
            buffer.get(messageBytes);
            byte[] messageHash = org.web3j.crypto.Hash.sha3(messageBytes);

            // toEthSignedMessageHash
            String prefix = "\u0019Ethereum Signed Message:\n32";
            byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
            byte[] prefixedMessage = new byte[prefixBytes.length + messageHash.length];
            System.arraycopy(prefixBytes, 0, prefixedMessage, 0, prefixBytes.length);
            System.arraycopy(messageHash, 0, prefixedMessage, prefixBytes.length, messageHash.length);
            byte[] ethHash = org.web3j.crypto.Hash.sha3(prefixedMessage);

            Credentials credentials = Credentials.create(
                adminPrivateKey.startsWith("0x") ? adminPrivateKey : "0x" + adminPrivateKey
            );
            Sign.SignatureData signatureData = Sign.signMessage(
                ethHash,
                credentials.getEcKeyPair(),
                false
            );

            byte[] r = signatureData.getR();
            byte[] s = signatureData.getS();
            byte[] v = signatureData.getV();

            byte[] signature = new byte[65];
            System.arraycopy(r, 0, signature, 0, 32);
            System.arraycopy(s, 0, signature, 32, 32);
            System.arraycopy(v, 0, signature, 64, 1);

            return Numeric.toHexString(signature);
        } catch (Exception e) {
            throw new RuntimeException("签名失败: " + e.getMessage(), e);
        }
    }

    private byte[] toBytes32(BigInteger value) {
        byte[] data = value.toByteArray();
        byte[] result = new byte[32];

        if (data.length == 32) {
            return data;
        } else if (data.length > 32) {
            System.arraycopy(data, data.length - 32, result, 0, 32);
        } else {
            System.arraycopy(data, 0, result, 32 - data.length, data.length);
        }

        return result;
    }

    @lombok.Data
    @lombok.Builder
    private static class TokenUsageGuide {
        private String[] earnWays;
        private String[] useWays;
        private String tokenSymbol;
        private String network;
        private String contractAddress;
    }
}
