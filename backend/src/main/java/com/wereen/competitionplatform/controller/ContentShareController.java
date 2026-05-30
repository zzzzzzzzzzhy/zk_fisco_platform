package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.conig.IpfsProperties;
import com.wereen.competitionplatform.conig.MinioProperties;
import com.wereen.competitionplatform.model.dto.content.CreateContentShareRequest;
import com.wereen.competitionplatform.model.entity.ContentShare;
import com.wereen.competitionplatform.service.ContentShareBlockchainService;
import com.wereen.competitionplatform.service.ContentShareService;
import com.wereen.competitionplatform.service.ContentReportService;
import com.wereen.competitionplatform.service.MinioService;
import com.wereen.competitionplatform.service.PolygonContentProofService;
import com.wereen.competitionplatform.service.RewardEventService;
import com.wereen.competitionplatform.service.UnifiedStorageService;
import com.wereen.competitionplatform.service.UserService;
import com.wereen.competitionplatform.model.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 内容分享控制器
 */
@RestController
@RequestMapping("/content-shares")
@RequiredArgsConstructor
public class ContentShareController {

    private final ContentShareService contentShareService;
    private final MinioService minioService;
    private final MinioProperties minioProperties;
    private final IpfsProperties ipfsProperties;
    private final UnifiedStorageService unifiedStorageService;
    private final PolygonContentProofService polygonContentProofService;
    private final ContentShareBlockchainService blockchainService;
    private final ContentReportService contentReportService;
    private final UserService userService;
    private final RewardEventService rewardEventService;
    private final ObjectMapper objectMapper;

    private void assertCanRead(ContentShare share) {
        if (share == null) {
            throw new com.wereen.competitionplatform.exception.BusinessException("内容不存在");
        }
        Integer visibility = share.getVisibility();
        Integer reviewStatus = share.getReviewStatus();
        boolean isPublicReviewed = Integer.valueOf(1).equals(visibility) && Integer.valueOf(1).equals(reviewStatus);
        if (isPublicReviewed) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long)
            ? (Long) auth.getPrincipal()
            : null;
        if (currentUserId == null) {
            throw new com.wereen.competitionplatform.exception.BusinessException("内容未审核或已下线");
        }
        if (currentUserId.equals(share.getUserId())) {
            return;
        }
        User user = userService.getUserById(currentUserId);
        String role = user != null && user.getRole() != null ? user.getRole() : UserRole.USER;
        if (UserRole.ADMIN.equals(role)) {
            return;
        }
        throw new com.wereen.competitionplatform.exception.BusinessException("内容未审核或已下线");
    }

    /**
     * 获取内容分享的预签名上传URL
     */
    @GetMapping("/presigned-url")
    public Result<Map<String, String>> getPresignedUploadUrl(
        @RequestParam String mediaType,
        @RequestParam String fileName
    ) {
        String sanitizedType = mediaType != null ? mediaType.toUpperCase() : "IMAGE";
        String bucketName = resolveBucket();
        String safeFileName = sanitizeObjectKeySegment(fileName);
        String objectName = String.format("content-share/%s/%s",
            sanitizedType.toLowerCase(), safeFileName);

        String uploadUrl;
        Map<String, String> result = new HashMap<>();

        // 根据存储提供商选择上传方式
        if (unifiedStorageService.isUsingIpfs()) {
            // IPFS不支持预签名URL，使用直接上传
            uploadUrl = "/content-shares/upload"; // 前端通过 /api 代理直传后端
            result.put("uploadMethod", "direct");
            result.put("message", "IPFS存储，请使用直接上传");
        } else {
            // MinIO使用预签名URL
            uploadUrl = minioService.getPresignedUploadUrl(bucketName, objectName, 60);
            result.put("uploadMethod", "presigned");
        }

        result.put("uploadUrl", uploadUrl);
        result.put("bucket", bucketName);
        result.put("objectName", objectName);
        result.put("storageProvider", unifiedStorageService.getCurrentProvider());

        return Result.success(result);
    }

    /**
     * IPFS 模式下前端直传文件到后端（也可用于 MinIO 直传）
     */
    @RequestMapping(
        value = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        method = {RequestMethod.POST, RequestMethod.PUT}
    )
    public Result<Map<String, String>> uploadContentShareFile(
        @RequestParam String mediaType,
        @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        String sanitizedType = mediaType != null ? mediaType.toUpperCase() : "IMAGE";
        String originalName = file.getOriginalFilename();
        String safeName = (originalName == null ? "file" : originalName)
            .replace("\\", "/");
        if (safeName.contains("/")) {
            safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        }
        safeName = sanitizeObjectKeySegment(safeName);

        String bucketName = resolveBucket();
        String objectName = String.format("content-share/%s/%s_%s",
            sanitizedType.toLowerCase(),
            UUID.randomUUID().toString().replace("-", ""),
            safeName
        );

        String uploadResult = unifiedStorageService.uploadFile(bucketName, objectName, file);
        String mediaUrl;
        if (unifiedStorageService.isUsingIpfs() && org.springframework.util.StringUtils.hasText(uploadResult) && uploadResult.startsWith("http")) {
            // IPFS 上传返回 CID 网关 URL
            mediaUrl = uploadResult;
        } else {
            mediaUrl = unifiedStorageService.getPublicUrl(bucketName, objectName);
        }

        Map<String, String> result = new HashMap<>();
        result.put("bucket", bucketName);
        result.put("objectName", objectName);
        result.put("mediaUrl", mediaUrl);
        if (unifiedStorageService.isUsingIpfs() && mediaUrl != null && mediaUrl.contains("/ipfs/")) {
            String after = mediaUrl.substring(mediaUrl.lastIndexOf("/ipfs/") + "/ipfs/".length());
            int q = after.indexOf('?');
            if (q >= 0) {
                after = after.substring(0, q);
            }
            int slash = after.indexOf('/');
            if (slash >= 0) {
                after = after.substring(0, slash);
            }
            result.put("cid", after);
        }
        result.put("storageProvider", unifiedStorageService.getCurrentProvider());
        return Result.success(result);
    }

    /**
     * 4Everland S3 兼容接口对对象 key 的 URL 编码兼容性不稳定：
     * 为避免签名计算差异导致 SignatureDoesNotMatch，这里将文件名段落规范化为 ASCII 安全字符。
     */
    private String sanitizeObjectKeySegment(String name) {
        String v = name == null ? "" : name.trim();
        if (v.isEmpty()) {
            return "file";
        }
        v = v.replace("\\", "/");
        if (v.contains("/")) {
            v = v.substring(v.lastIndexOf('/') + 1);
        }
        // 保留扩展名（只保留最后一个 . 之后的部分）
        String ext = "";
        int dot = v.lastIndexOf('.');
        if (dot > 0 && dot < v.length() - 1) {
            ext = v.substring(dot + 1);
            v = v.substring(0, dot);
        }
        v = v.replaceAll("[\\s]+", "_");
        // 只允许常见安全字符，其余统一替换为 _
        v = v.replaceAll("[^A-Za-z0-9._-]", "_");
        v = v.replaceAll("_+", "_");
        v = v.replaceAll("^_+|_+$", "");
        if (v.isEmpty()) {
            v = "file";
        }
        if (!ext.isEmpty()) {
            ext = ext.replaceAll("[^A-Za-z0-9]", "");
            if (!ext.isEmpty()) {
                return v + "." + ext;
            }
        }
        return v;
    }

    /**
     * 创建内容分享
     */
    @PostMapping
    public Result<ContentShare> createContentShare(@Valid @RequestBody CreateContentShareRequest request) {
        ContentShare share = contentShareService.createContentShare(request);
        return Result.success(share);
    }

    /**
     * 内容分享用户确认签名（不直接上链）
     */
    @PostMapping("/{id}/consent")
    public Result<Map<String, Object>> submitContentShareConsent(
        @PathVariable Long id,
        @RequestBody Map<String, String> request
    ) {
        ContentShare share = contentShareService.getById(id);
        if (share == null) {
            return Result.error("内容不存在");
        }
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(share.getUserId())) {
            return Result.error("无权限提交签名");
        }
        String signature = request.get("signature");
        String userAddress = request.get("userAddress");
        if (!StringUtils.hasText(signature)) {
            return Result.error("签名不能为空");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("shareId", share.getId());
        payload.put("mediaType", share.getMediaType());
        payload.put("fileHash", share.getFileHash());
        payload.put("userAddress", userAddress);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        rewardEventService.createEvent(
            currentUserId,
            "CONTENT_SHARE",
            "share_" + share.getId(),
            signature,
            payloadJson
        );

        Map<String, Object> result = new HashMap<>();
        result.put("accepted", true);
        return Result.success(result);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    /**
     * 获取内容分享详情
     */
    @GetMapping("/{id}")
    public Result<ContentShare> getContentShare(@PathVariable Long id) {
        ContentShare share = contentShareService.getById(id);
        assertCanRead(share);
        return Result.success(share);
    }

    /**
     * 代理媒体文件访问（避免客户端直连 IPFS 网关 / MinIO 端口导致 DNS/防火墙问题）
     */
    @GetMapping("/{id}/media")
    public ResponseEntity<InputStreamResource> proxyMedia(@PathVariable Long id) {
        ContentShare share;
        try {
            share = contentShareService.getById(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
        try {
            assertCanRead(share);
        } catch (Exception e) {
            return ResponseEntity.status(403).build();
        }

        String bucket = null;
        String objectName = null;
        String contentType = null;

        if (StringUtils.hasText(share.getMetadata())) {
            try {
                com.alibaba.fastjson2.JSONObject meta = com.alibaba.fastjson2.JSON.parseObject(share.getMetadata());
                bucket = meta.getString("bucket");
                objectName = meta.getString("objectName");
                contentType = meta.getString("contentType");
            } catch (Exception ignore) {
            }
        }

        // 兜底：MinIO 场景下允许从 mediaUrl 推断 objectName
        if (!StringUtils.hasText(objectName) && StringUtils.hasText(share.getMediaUrl())) {
            try {
                String originalUrl = share.getMediaUrl();
                int idx = originalUrl.indexOf("://");
                String pathPart = idx > 0 ? originalUrl.substring(originalUrl.indexOf('/', idx + 3)) : originalUrl;
                if (pathPart.startsWith("/")) {
                    pathPart = pathPart.substring(1);
                }
                objectName = pathPart;
            } catch (Exception ignore) {
            }
        }

        if (!StringUtils.hasText(bucket)) {
            if (unifiedStorageService.isUsingIpfs()) {
                bucket = ipfsProperties.getBucketName();
            } else if (minioProperties.getBuckets() != null && StringUtils.hasText(minioProperties.getBuckets().get("content-share"))) {
                bucket = minioProperties.getBuckets().get("content-share");
            } else {
                bucket = minioProperties.getBucketName();
            }
        }

        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectName)) {
            return ResponseEntity.notFound().build();
        }

        java.io.InputStream in = unifiedStorageService.downloadFile(bucket, objectName);
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(contentType)) {
            try {
                mt = MediaType.parseMediaType(contentType);
            } catch (Exception ignore) {
            }
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
            .contentType(mt)
            .body(new InputStreamResource(in));
    }

    /**
     * 手动重试 FISCO 存证（需要登录；管理员可用于补偿失败记录）
     */
    @PostMapping("/{id}/fisco-retry")
    public Result<Boolean> retryFiscoProof(@PathVariable Long id) {
        ContentShare share = contentShareService.getById(id);
        if (share == null) {
            return Result.error("内容不存在");
        }
        blockchainService.pushToChainsAsync(share.getId(), share.getUserId(), share.getMediaType());
        return Result.success(true);
    }

    /**
     * 分页查询内容分享
     */
    @GetMapping
    public Result<PageResult<ContentShare>> listContentShares(
        @RequestParam(defaultValue = "1") Long current,
        @RequestParam(defaultValue = "12") Long size,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String mediaType
    ) {
        PageResult<ContentShare> page = contentShareService.listShares(current, size, userId, mediaType);
        return Result.success(page);
    }

    /**
     * 删除内容分享（软删除）- 仅管理员
     */
    @DeleteMapping("/{id}")
    @RequireRole(UserRole.ADMIN)
    public Result<Boolean> deleteContentShare(@PathVariable Long id) {
        boolean result = contentShareService.deleteContentShare(id);
        return Result.success(result);
    }

    /**
     * 管理员获取内容分享列表（包含已下线内容）
     */
    @GetMapping("/admin/list")
    @RequireRole(UserRole.ADMIN)
    public Result<PageResult<ContentShare>> adminListContentShares(
        @RequestParam(defaultValue = "1") Long current,
        @RequestParam(defaultValue = "24") Long size,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String mediaType,
        @RequestParam(required = false) String title,
        @RequestParam(required = false) Integer visibility,
        @RequestParam(required = false) Integer reviewStatus
    ) {
        PageResult<ContentShare> page = contentShareService.listSharesForAdmin(
            current, size, userId, mediaType, title, visibility, reviewStatus);
        return Result.success(page);
    }

    /**
     * 切换内容可见性（上线/下线）- 仅管理员
     */
    @PutMapping("/{id}/visibility")
    @RequireRole(UserRole.ADMIN)
    public Result<Boolean> toggleContentVisibility(@PathVariable Long id, @RequestParam Integer visibility) {
        boolean result = contentShareService.updateContentVisibility(id, visibility);
        return Result.success(result);
    }
    
    /**
     * 获取 Polygon 存证所需的 EIP-712 签名数据
     * 用户在前端使用 MetaMask 签名后调用合约
     */
    @GetMapping("/{id}/polygon-sign-data")
    public Result<Map<String, Object>> getPolygonSignData(@PathVariable Long id) {
        ContentShare share = contentShareService.getById(id);
        if (share == null) {
            return Result.error("内容不存在");
        }
        // 未审核/下线内容仅允许作者或管理员请求签名数据
        assertCanRead(share);
        if (!org.springframework.util.StringUtils.hasText(share.getFileHash())) {
            try {
                String bucket = null;
                String objectName = null;
                if (org.springframework.util.StringUtils.hasText(share.getMetadata())) {
                    com.alibaba.fastjson2.JSONObject meta = com.alibaba.fastjson2.JSON.parseObject(share.getMetadata());
                    bucket = meta.getString("bucket");
                    objectName = meta.getString("objectName");
                }
                if (!org.springframework.util.StringUtils.hasText(objectName) && org.springframework.util.StringUtils.hasText(share.getMediaUrl())) {
                    String originalUrl = share.getMediaUrl();
                    int idx = originalUrl.indexOf("://");
                    String pathPart = idx > 0 ? originalUrl.substring(originalUrl.indexOf('/', idx + 3)) : originalUrl;
                    if (pathPart.startsWith("/")) {
                        pathPart = pathPart.substring(1);
                    }
                    objectName = pathPart;
                }
                if (!org.springframework.util.StringUtils.hasText(bucket) && minioProperties.getBuckets() != null) {
                    bucket = minioProperties.getBuckets().get("content-share");
                }
                if (!org.springframework.util.StringUtils.hasText(bucket)) {
                    bucket = minioProperties.getBucketName();
                }
                if (org.springframework.util.StringUtils.hasText(bucket) && org.springframework.util.StringUtils.hasText(objectName)) {
                    try (java.io.InputStream in = minioService.downloadFile(bucket, objectName)) {
                        String computed = com.wereen.competitionplatform.util.FileHashCalculator.calculateSHA256(in);
                        contentShareService.updateFileHash(id, "SHA256", computed);
                        share.setHashAlgorithm("SHA256");
                        share.setFileHash(computed);
                    }
                } else {
                    return Result.error("无法确定文件存储位置，计算哈希失败");
                }
            } catch (Exception e) {
                return Result.error("计算文件哈希失败: " + e.getMessage());
            }
        }
        
        // 获取当前登录用户的钱包地址
        String publisherAddress = contentShareService.getCurrentUserWalletAddress();
        if (publisherAddress == null || publisherAddress.isEmpty()) {
            return Result.error("请先绑定钱包地址");
        }
        
        Map<String, Object> signData = polygonContentProofService.generateEIP712Data(
            share.getId(),
            share.getFileHash(),
            share.getMetadata() != null ? share.getMetadata() : "{}",
            publisherAddress
        );
        
        return Result.success(signData);
    }
    
    /**
     * 用户提交 Polygon 存证交易 Hash，后端验证并发放奖励
     */
    @PostMapping("/{id}/polygon-proof")
    public Result<String> submitPolygonProof(
        @PathVariable Long id,
        @RequestBody Map<String, String> request
    ) {
        String txHash = request.get("txHash");
        if (txHash == null || txHash.isEmpty()) {
            return Result.error("交易哈希不能为空");
        }
        
        ContentShare share = contentShareService.getById(id);
        if (share == null) {
            return Result.error("内容不存在");
        }
        
        try {
            blockchainService.verifyAndRewardPolygonProof(
                id, 
                txHash, 
                share.getUserId(), 
                share.getMediaType()
            );
            return Result.success("Polygon 存证验证成功，WEE 奖励已发放");
        } catch (Exception e) {
            return Result.error("验证失败: " + e.getMessage());
        }
    }

    /**
     * 用户举报内容
     */
    @PostMapping("/{id}/report")
    public Result<Boolean> reportContent(
        @PathVariable Long id,
        @RequestBody Map<String, String> request
    ) {
        String reasonCode = request.getOrDefault("reasonCode", "OTHER");
        String reasonText = request.get("reasonText");

        // 获取当前登录用户ID（允许未登录，reporterId 为空）
        Long reporterId = null;
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof Long) {
                reporterId = (Long) auth.getPrincipal();
            }
        } catch (Exception ignored) {
        }

        contentReportService.createReport(id, reporterId, reasonCode, reasonText);
        return Result.success(true);
    }

    /**
     * 审核内容分享（仅管理员）
     */
    @PutMapping("/{id}/review")
    @RequireRole(UserRole.ADMIN)
    public Result<Boolean> reviewContent(
        @PathVariable Long id,
        @RequestBody Map<String, String> request
    ) {
        Integer status;
        try {
            status = Integer.valueOf(request.getOrDefault("status", "0"));
        } catch (NumberFormatException e) {
            return Result.error("无效的审核状态");
        }
        String reason = request.get("reason");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long reviewerId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            reviewerId = (Long) authentication.getPrincipal();
        }

        contentShareService.reviewContent(id, status, reason, reviewerId);
        return Result.success(true);
    }

    private String resolveBucket() {
        if (unifiedStorageService.isUsingIpfs()) {
            // IPFS使用配置的桶名
            return ipfsProperties.getBucketName();
        } else {
            // MinIO逻辑
            if (minioProperties.getBuckets() != null) {
                String bucket = minioProperties.getBuckets().get("content-share");
                if (StringUtils.hasText(bucket)) {
                    return bucket;
                }
            }
            return minioProperties.getBucketName();
        }
    }
}
