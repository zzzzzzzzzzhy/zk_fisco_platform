package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.service.CaptchaService;
import com.wereen.competitionplatform.utils.IpUtils;
import com.wereen.competitionplatform.service.RewardEventService;
import com.wereen.competitionplatform.service.WeeBalanceService;
import com.wereen.competitionplatform.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 签到控制器 - 提供签名服务
 */
@Slf4j
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    @Value("${blockchain.admin.private-key}")
    private String adminPrivateKey;
    
    @Value("${blockchain.forum-token.extension-address}")
    private String forumExtensionAddress;
    
    private final CaptchaService captchaService;
    private final RewardEventService rewardEventService;
    private final WeeBalanceService weeBalanceService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /**
     * 获取签到签名（需要验证码令牌）
     */
    @PostMapping("/sign")
    public Result<Map<String, Object>> getCheckinSignature(
            @RequestParam String userAddress,
            @RequestParam(required = false) String captchaToken,
            HttpServletRequest request
    ) {
        try {
            // 验证地址格式
            if (!userAddress.matches("^0x[a-fA-F0-9]{40}$")) {
                return Result.error("无效的地址格式");
            }
            
            // 规范化地址（统一转小写）
            userAddress = userAddress.toLowerCase();
            
            // 验证验证码（如果提供了令牌）
            // 当前版本先不在后端强校验验证码，仅作为前端防刷手段，
            // 主要安全由签名 + 合约限制保证，后续如需更强风控可在此接入服务端校验逻辑。
            if (captchaToken != null && !captchaToken.isEmpty()) {
                String userIp = IpUtils.getClientIp(request);
                log.debug("收到前端验证码令牌 captchaToken={}, ip={}", captchaToken, userIp);
                // TODO: 可在此接入 Redis / 第三方风控服务做服务端校验
            }
            
            // 获取当前天数（UTC 天数）
            long day = Instant.now().getEpochSecond() / 86400;
            
            // 设置过期时间（5分钟后）
            long deadline = Instant.now().getEpochSecond() + 300;
            
            // 构造 bizId（与合约一致）
            String bizId = "checkin_" + day;
            
            // 生成签名（按照合约的 _hashRewardMessage 格式）
            String signature = signRewardMessage(
                userAddress,
                2, // RewardType.DAILY_CHECKIN = 2
                bizId,
                deadline
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("user", userAddress);
            response.put("day", day);
            response.put("deadline", deadline);
            response.put("signature", signature);
            
            log.info("Generated checkin signature for user: {}, day: {}", userAddress, day);
            
            return Result.success(response);
        } catch (Exception e) {
            log.error("生成签到签名失败", e);
            return Result.error("生成签名失败: " + e.getMessage());
        }
    }

    /**
     * 提交签到确认签名（用户签名，不直接上链）
     */
    @PostMapping("/consent")
    public Result<Map<String, Object>> submitCheckinConsent(@RequestBody Map<String, String> request) {
        String userAddress = request.get("userAddress");
        String signature = request.get("signature");
        String day = request.get("day");
        if (!StringUtils.hasText(userAddress) || !userAddress.matches("^0x[a-fA-F0-9]{40}$")) {
            return Result.error("无效的用户地址");
        }
        if (!StringUtils.hasText(signature)) {
            return Result.error("签名不能为空");
        }
        if (!StringUtils.hasText(day)) {
            day = LocalDate.now(ZoneOffset.UTC).toString();
        }

        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("无法识别用户");
        }

        String bizId = "checkin_" + day.replace("-", "");
        Map<String, Object> payload = new HashMap<>();
        payload.put("day", day);
        payload.put("userAddress", userAddress.toLowerCase());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        rewardEventService.createEvent(userId, "CHECKIN", bizId, signature, payloadJson);
        Map<String, Object> response = new HashMap<>();
        response.put("accepted", true);
        response.put("day", day);
        return Result.success(response);
    }

    /**
     * 查询今日是否已提交签到
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> checkinStatus() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("无法识别用户");
        }
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        String bizId = "checkin_" + day.replace("-", "");
        boolean exists = rewardEventService.existsEvent(userId, "CHECKIN", bizId);
        Map<String, Object> response = new HashMap<>();
        response.put("day", day);
        response.put("submitted", exists);
        return Result.success(response);
    }

    /**
     * 每日签到（无需钱包，直接 DB 记录 + WEE 奖励）
     */
    @PostMapping("/daily")
    public Result<Map<String, Object>> dailyCheckin() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        String bizId = "checkin_" + day.replace("-", "");

        boolean alreadyChecked = rewardEventService.existsEvent(userId, "CHECKIN", bizId);
        Map<String, Object> response = new HashMap<>();
        response.put("day", day);

        if (alreadyChecked) {
            response.put("success", false);
            response.put("message", "今日已签到");
            response.put("reward", 0);
            return Result.success(response);
        }

        rewardEventService.createEvent(userId, "CHECKIN", bizId, "", "{}");
        weeBalanceService.addReward(userId, WeeBalanceService.REWARD_CHECKIN, "每日签到");
        long newBalance = weeBalanceService.getBalance(userId);

        response.put("success", true);
        response.put("message", "签到成功，+" + WeeBalanceService.REWARD_CHECKIN + " WEE");
        response.put("reward", WeeBalanceService.REWARD_CHECKIN);
        response.put("balance", newBalance);
        return Result.success(response);
    }

    /**
     * 签名奖励消息（按照合约 _hashRewardMessage 的格式）
     *
     * 合约格式：
     * msgHash = keccak256(abi.encodePacked("WEE_REWARD", address(this), block.chainid, user, rewardType, bizId, deadline))
     * ethHash = toEthSignedMessageHash(msgHash)
     * signer = recover(ethHash, signature)
     */
    private String signRewardMessage(String userAddress, int rewardType, String bizId, long deadline) {
        try {
            // 1. 按照 abi.encodePacked 的方式构造消息
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            // "WEE_REWARD" 字符串（直接拼接，不补齐）
            buffer.put("WEE_REWARD".getBytes(StandardCharsets.UTF_8));
            
            // address(this) - 合约地址（20 bytes）
            buffer.put(Numeric.hexStringToByteArray(forumExtensionAddress));
            
            // block.chainid (uint256 = 32 bytes)
            buffer.put(toBytes32(BigInteger.valueOf(137)));
            
            // user 地址（20 bytes）
            buffer.put(Numeric.hexStringToByteArray(userAddress));
            
            // rewardType (uint8，在 abi.encodePacked 中是 1 byte)
            buffer.put((byte) rewardType);
            
            // bizId (string，直接拼接)
            buffer.put(bizId.getBytes(StandardCharsets.UTF_8));
            
            // deadline (uint256 = 32 bytes)
            buffer.put(toBytes32(BigInteger.valueOf(deadline)));
            
            // 计算 keccak256
            byte[] messageBytes = new byte[buffer.position()];
            buffer.flip();
            buffer.get(messageBytes);
            byte[] messageHash = org.web3j.crypto.Hash.sha3(messageBytes);
            
            // 2. 添加 Ethereum 签名前缀（toEthSignedMessageHash）
            String prefix = "\u0019Ethereum Signed Message:\n32";
            byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
            byte[] prefixedMessage = new byte[prefixBytes.length + messageHash.length];
            System.arraycopy(prefixBytes, 0, prefixedMessage, 0, prefixBytes.length);
            System.arraycopy(messageHash, 0, prefixedMessage, prefixBytes.length, messageHash.length);
            byte[] ethHash = org.web3j.crypto.Hash.sha3(prefixedMessage);
            
            // 3. 使用私钥签名
            Credentials credentials = Credentials.create(
                adminPrivateKey.startsWith("0x") ? adminPrivateKey : "0x" + adminPrivateKey
            );
            Sign.SignatureData signatureData = Sign.signMessage(
                ethHash,
                credentials.getEcKeyPair(),
                false
            );
            
            // 4. 组合签名 (r + s + v)
            byte[] r = signatureData.getR();
            byte[] s = signatureData.getS();
            byte[] v = signatureData.getV();
            
            byte[] signature = new byte[65];
            System.arraycopy(r, 0, signature, 0, 32);
            System.arraycopy(s, 0, signature, 32, 32);
            System.arraycopy(v, 0, signature, 64, 1);
            
            return Numeric.toHexString(signature);
        } catch (Exception e) {
            log.error("签名失败", e);
            throw new RuntimeException("签名失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将 BigInteger 转为 32 字节数组（uint256）
     */
    private byte[] toBytes32(BigInteger value) {
        byte[] data = value.toByteArray();
        byte[] result = new byte[32];
        
        if (data.length == 32) {
            return data;
        } else if (data.length > 32) {
            // 去掉前面的符号位
            System.arraycopy(data, data.length - 32, result, 0, 32);
        } else {
            // 在前面补 0
            System.arraycopy(data, 0, result, 32 - data.length, data.length);
        }
        
        return result;
    }

    private Long getCurrentUserId() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                 org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();
            String bearer = request.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                return jwtUtil.getUserIdFromToken(bearer.substring(7));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
