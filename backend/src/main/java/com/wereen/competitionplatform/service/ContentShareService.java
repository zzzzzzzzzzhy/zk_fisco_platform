package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.ContentPinMapper;
import com.wereen.competitionplatform.mapper.ContentShareMapper;
import com.wereen.competitionplatform.mapper.ContentTipMapper;
import com.wereen.competitionplatform.mapper.UserMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.wereen.competitionplatform.model.dto.content.CreateContentShareRequest;
import com.wereen.competitionplatform.model.dto.content.ContentTipSummary;
import com.wereen.competitionplatform.model.entity.ContentPin;
import com.wereen.competitionplatform.model.entity.ContentShare;
import com.wereen.competitionplatform.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容分享服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentShareService {

    private final ContentShareMapper contentShareMapper;
    private final ContentShareBlockchainService contentShareBlockchainService;
    private final ContentTipMapper contentTipMapper;
    private final ContentPinMapper contentPinMapper;
    private final UserMapper userMapper;
    private final UnifiedStorageService unifiedStorageService;
    private final com.wereen.competitionplatform.service.MinioService minioService;
    private final com.wereen.competitionplatform.conig.MinioProperties minioProperties;
    private final com.wereen.competitionplatform.conig.IpfsProperties ipfsProperties;
    private final com.wereen.competitionplatform.service.IpfsService ipfsService;

    /**
     * 创建内容分享
     */
    @Transactional(rollbackFor = Exception.class)
    public ContentShare createContentShare(CreateContentShareRequest request) {
        validateRequest(request);

        ContentShare share = new ContentShare();
        share.setUserId(request.getUserId());
        share.setTitle(request.getTitle());
        share.setDescription(request.getDescription());
        share.setMediaType(request.getMediaType().toUpperCase());
        share.setMediaUrl(request.getMediaUrl());
        share.setThumbnailUrl(request.getThumbnailUrl());
        share.setDurationSeconds(request.getDurationSeconds());
        String algo = StringUtils.hasText(request.getHashAlgorithm()) ? request.getHashAlgorithm().toUpperCase() : "SHA256";
        share.setHashAlgorithm(algo);
        String incomingHash = request.getFileHash();
        if (!StringUtils.hasText(incomingHash)) {
            log.info("前端未提供文件哈希，开始计算后端哈希");
            String bucket = null;
            String objectName = null;
            try {
                if (StringUtils.hasText(request.getMetadata())) {
                    JSONObject meta = JSON.parseObject(request.getMetadata());
                    bucket = meta.getString("bucket");
                    objectName = meta.getString("objectName");
                    log.info("从metadata解析: bucket={}, objectName={}", bucket, objectName);
                }
                if (!StringUtils.hasText(objectName) && StringUtils.hasText(request.getMediaUrl()) && !unifiedStorageService.isUsingIpfs()) {
                    String originalUrl = request.getMediaUrl();
                    int idx = originalUrl.indexOf("://");
                    String pathPart = idx > 0 ? originalUrl.substring(originalUrl.indexOf('/', idx + 3)) : originalUrl;
                    if (pathPart.startsWith("/")) {
                        pathPart = pathPart.substring(1);
                    }
                    objectName = pathPart;
                    log.info("从mediaUrl解析: objectName={}", objectName);
                }
                if (unifiedStorageService.isUsingIpfs()) {
                    if (!StringUtils.hasText(bucket)) {
                        bucket = ipfsProperties.getBucketName();
                    }
                    if (StringUtils.hasText(bucket) && StringUtils.hasText(objectName)) {
                        log.info("尝试从IPFS下载文件计算哈希: bucket={}, objectName={}", bucket, objectName);
                        try (java.io.InputStream in = unifiedStorageService.downloadFile(bucket, objectName)) {
                            String computed = com.wereen.competitionplatform.util.FileHashCalculator.calculateSHA256(in);
                            incomingHash = computed;
                            log.info("后端哈希计算成功: hash={}", incomingHash);
                        }
                    } else {
                        log.error("IPFS 模式下缺少 bucket/objectName，无法计算哈希: bucket={}, objectName={}", bucket, objectName);
                        throw new BusinessException("IPFS 模式下无法确定文件存储位置，无法计算哈希");
                    }
                } else {
                    if (!StringUtils.hasText(bucket) && minioProperties.getBuckets() != null) {
                        bucket = minioProperties.getBuckets().get("content-share");
                    }
                    if (!StringUtils.hasText(bucket)) {
                        bucket = minioProperties.getBucketName();
                    }
                    if (StringUtils.hasText(bucket) && StringUtils.hasText(objectName)) {
                        log.info("尝试从MinIO下载文件计算哈希: bucket={}, objectName={}", bucket, objectName);
                        try (java.io.InputStream in = minioService.downloadFile(bucket, objectName)) {
                            String computed = com.wereen.competitionplatform.util.FileHashCalculator.calculateSHA256(in);
                            incomingHash = computed;
                            log.info("后端哈希计算成功: hash={}", incomingHash);
                        }
                    } else {
                        log.error("无法确定文件位置: bucket={}, objectName={}", bucket, objectName);
                        throw new BusinessException("无法确定文件存储位置，无法计算哈希");
                    }
                }
            } catch (Exception e) {
                log.error("文件哈希计算失败: bucket={}, objectName={}, error={}", bucket, objectName, e.getMessage(), e);
                throw new BusinessException("文件哈希计算失败: " + e.getMessage());
            }
        } else {
            log.info("使用前端提供的哈希: hash={}", incomingHash);
        }

        // 确保文件哈希不为空
        if (!StringUtils.hasText(incomingHash)) {
            log.error("文件哈希为空，无法创建内容分享");
            throw new BusinessException("文件哈希不能为空");
        }

        share.setFileHash(incomingHash);
        share.setMetadata(request.getMetadata());
        // 默认进入审核流程：未审核内容不应在公开列表中展示
        share.setVisibility(0);
        share.setReviewStatus(0);
        share.setFiscoStatus(0);
        share.setPolygonStatus(0);

        int rows = contentShareMapper.insert(share);
        if (rows <= 0) {
            throw new BusinessException("创建内容分享失败");
        }

        Long shareId = share.getId();
        Long publisherId = share.getUserId();
        String mediaType = share.getMediaType();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("内容分享创建完成，触发链上存证: shareId={}", shareId);
                // 先存证，存证完成后再发奖励（在 ContentShareBlockchainService 里调用）
                contentShareBlockchainService.pushToChainsAsync(shareId, publisherId, mediaType);
            }
        });

        return share;
    }

    public void updateFileHash(Long id, String algorithm, String fileHash) {
        ContentShare s = new ContentShare();
        s.setId(id);
        if (StringUtils.hasText(algorithm)) {
            s.setHashAlgorithm(algorithm.toUpperCase());
        }
        s.setFileHash(fileHash);
        contentShareMapper.updateById(s);
    }

    /**
     * 根据ID查询
     */
    public ContentShare getById(Long id) {
        ContentShare share = contentShareMapper.selectById(id);
        if (share == null) {
            throw new BusinessException("内容分享不存在");
        }
        enrichShares(Collections.singletonList(share));
        return share;
    }

    /**
     * 分页查询
     */
    public PageResult<ContentShare> listShares(Long current, Long size, Long userId, String mediaType) {
        Page<ContentShare> page = new Page<>(current, size);
        LambdaQueryWrapper<ContentShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentShare::getVisibility, 1); // 只查询公开的记录
        wrapper.eq(ContentShare::getReviewStatus, 1); // 只展示审核通过的内容

        if (userId != null) {
            wrapper.eq(ContentShare::getUserId, userId);
        }
        if (StringUtils.hasText(mediaType)) {
            wrapper.eq(ContentShare::getMediaType, mediaType.toUpperCase());
        }
        wrapper.orderByDesc(ContentShare::getCreatedAt);

        Page<ContentShare> resultPage = contentShareMapper.selectPage(page, wrapper);
        List<ContentShare> records = resultPage.getRecords();
        enrichShares(records);
        if (!CollectionUtils.isEmpty(records)) {
            records.sort((a, b) -> {
                boolean bPinned = Boolean.TRUE.equals(b.getPinned());
                boolean aPinned = Boolean.TRUE.equals(a.getPinned());
                if (aPinned != bPinned) {
                    return Boolean.compare(bPinned, aPinned);
                }
                LocalDateTime bTime = b.getCreatedAt();
                LocalDateTime aTime = a.getCreatedAt();
                if (bTime == null || aTime == null) {
                    return 0;
                }
                return bTime.compareTo(aTime);
            });
        }
        return new PageResult<>(
            resultPage.getTotal(),
            resultPage.getCurrent(),
            resultPage.getSize(),
            resultPage.getRecords()
        );
    }

    /**
     * 删除内容分享（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteContentShare(Long id) {
        ContentShare share = contentShareMapper.selectById(id);
        if (share == null) {
            throw new BusinessException("内容分享不存在");
        }

        // 软删除：标记为已下线
        share.setVisibility(0);
        int result = contentShareMapper.updateById(share);

        if (result > 0) {
            log.info("内容分享已删除: id={}, title={}", id, share.getTitle());
            return true;
        }

        return false;
    }

    /**
     * 管理员分页查询内容分享（包含已下线内容）
     */
    public PageResult<ContentShare> listSharesForAdmin(Long current, Long size, Long userId, String mediaType, String title, Integer visibility, Integer reviewStatus) {
        Page<ContentShare> page = new Page<>(current, size);
        LambdaQueryWrapper<ContentShare> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(ContentShare::getUserId, userId);
        }
        if (StringUtils.hasText(mediaType)) {
            wrapper.eq(ContentShare::getMediaType, mediaType.toUpperCase());
        }
        if (StringUtils.hasText(title)) {
            wrapper.like(ContentShare::getTitle, title);
        }
        if (visibility != null) {
            wrapper.eq(ContentShare::getVisibility, visibility);
        }
        if (reviewStatus != null) {
            wrapper.eq(ContentShare::getReviewStatus, reviewStatus);
        }
        wrapper.orderByDesc(ContentShare::getCreatedAt);

        Page<ContentShare> resultPage = contentShareMapper.selectPage(page, wrapper);
        List<ContentShare> records = resultPage.getRecords();
        enrichShares(records);

        return new PageResult<>(
            resultPage.getTotal(),
            resultPage.getCurrent(),
            resultPage.getSize(),
            resultPage.getRecords()
        );
    }

    /**
     * 更新内容可见性
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContentVisibility(Long id, Integer visibility) {
        ContentShare share = contentShareMapper.selectById(id);
        if (share == null) {
            throw new BusinessException("内容分享不存在");
        }

        share.setVisibility(visibility);
        int result = contentShareMapper.updateById(share);

        if (result > 0) {
            log.info("内容分享可见性已更新: id={}, visibility={}", id, visibility);
            return true;
        }

        return false;
    }

    /**
     * 更新内容置顶状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePinStatus(Long contentId, boolean pinned, LocalDateTime endTime) {
        ContentShare share = contentShareMapper.selectById(contentId);
        if (share == null) {
            log.warn("内容分享不存在，无法更新置顶状态: contentId={}", contentId);
            return;
        }

        // 暂时注释掉置顶功能，等系统稳定后再添加
        // share.setIsPinned(pinned);
        // if (pinned && endTime != null) {
        //     share.setPinEndTime(endTime);
        // } else {
        //     share.setPinEndTime(null);
        // }

        contentShareMapper.updateById(share);
        log.info("内容分享置顶状态已更新: contentId={}, pinned={}, endTime={}",
                contentId, pinned, endTime);
    }

    /**
     * 审核内容
     *
     * @param id           内容ID
     * @param reviewStatus 审核状态(1-通过 2-拒绝)
     * @param reviewReason 审核备注
     * @param reviewerId   审核人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewContent(Long id, Integer reviewStatus, String reviewReason, Long reviewerId) {
        ContentShare share = contentShareMapper.selectById(id);
        if (share == null) {
            throw new BusinessException("内容分享不存在");
        }
        if (reviewStatus == null || (reviewStatus != 1 && reviewStatus != 2)) {
            throw new BusinessException("无效的审核状态");
        }

        share.setReviewStatus(reviewStatus);
        share.setReviewReason(reviewReason);
        share.setReviewerId(reviewerId);
        share.setReviewedAt(LocalDateTime.now());

        // 审核通过自动公开，审核拒绝则保持/设置为下线
        if (reviewStatus == 1) {
            share.setVisibility(1);
        } else if (reviewStatus == 2) {
            share.setVisibility(0);
        }

        contentShareMapper.updateById(share);
        log.info("内容分享审核完成: id={}, status={}, reviewerId={}, reason={}",
                id, reviewStatus, reviewerId, reviewReason);
    }

    private void enrichShares(List<ContentShare> shares) {
        if (CollectionUtils.isEmpty(shares)) {
            return;
        }
        List<Long> shareIds = shares.stream()
            .map(ContentShare::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(shareIds)) {
            return;
        }

        Map<Long, ContentPin> pinMap = fetchActivePins(shareIds);
        Map<Long, BigDecimal> tipTotals = fetchTipTotals(shareIds);
        Map<Long, String> walletMap = fetchAuthorWallets(shares);

        for (ContentShare share : shares) {
            // 为前端返回可直接访问的带签名 URL，避免桶权限/CORS 问题
            try {
                enhanceMediaUrlWithPresignedLink(share);
            } catch (Exception e) {
                // 如果生成失败，不影响整体逻辑，保留原始 mediaUrl
                log.warn("生成内容分享预签名下载URL失败: shareId={}, mediaUrl={}", share.getId(), share.getMediaUrl(), e);
            }

            ContentPin pin = pinMap.get(share.getId());
            if (pin != null) {
                share.setPinned(true);
                share.setPinEndTime(pin.getEndTime());
            } else {
                share.setPinned(false);
                share.setPinEndTime(null);
            }
            BigDecimal total = tipTotals.get(share.getId());
            share.setTotalTips(total != null ? total : BigDecimal.ZERO);
            share.setAuthorWalletAddress(walletMap.get(share.getUserId()));
        }
    }

    /**
     * 基于 metadata 或原始 mediaUrl 生成带签名的下载 URL，并覆盖到 share.mediaUrl
     */
    private void enhanceMediaUrlWithPresignedLink(ContentShare share) {
        String bucket = null;
        String objectName = null;
        String storageProviderHint = null;
        String cidHint = null;

        // 图片统一走后端代理，避免浏览器直连外部网关/端口失败
        if ("IMAGE".equalsIgnoreCase(share.getMediaType())) {
            share.setMediaUrl("/api/content-shares/" + share.getId() + "/media");
            return;
        }

        // 1. 优先从 metadata 中解析 bucket 和 objectName
        JSONObject meta = null;
        if (StringUtils.hasText(share.getMetadata())) {
            try {
                meta = JSON.parseObject(share.getMetadata());
                bucket = meta.getString("bucket");
                objectName = meta.getString("objectName");
                storageProviderHint = meta.getString("storageProvider");
                cidHint = meta.getString("cid");
            } catch (Exception ignore) {
                // 解析失败则退化成从 mediaUrl 里猜
            }
        }

        // 2. 如果 metadata 里没有，就从 mediaUrl 中截取路径部分
        String originalUrl = share.getMediaUrl();
        if (!StringUtils.hasText(objectName) && StringUtils.hasText(originalUrl)) {
            try {
                // IPFS 网关 URL：.../ipfs/<cid>
                if (originalUrl.contains("/ipfs/")) {
                    String after = originalUrl.substring(originalUrl.lastIndexOf("/ipfs/") + "/ipfs/".length());
                    int q = after.indexOf('?');
                    if (q >= 0) {
                        after = after.substring(0, q);
                    }
                    int slash = after.indexOf('/');
                    if (slash >= 0) {
                        after = after.substring(0, slash);
                    }
                    objectName = after;
                    storageProviderHint = "ipfs";
                } else {
                    // 直接把 host 之后的路径当作 objectName，例如 /content-share/CONTENT/image/xxx.png
                    int idx = originalUrl.indexOf("://");
                    String pathPart = idx > 0 ? originalUrl.substring(originalUrl.indexOf('/', idx + 3)) : originalUrl;
                    if (pathPart.startsWith("/")) {
                        pathPart = pathPart.substring(1);
                    }
                    objectName = pathPart;
                }
            } catch (Exception ignore) {
                // 保底：保持 objectName 为空，不生成预签名 URL
            }
        }

        // 3. 解析桶名：优先 metadata，其次按“内容自身的存储类型”回退到配置
        String inferredProvider = inferProvider(storageProviderHint, cidHint, originalUrl, bucket);
        if ("ipfs".equalsIgnoreCase(inferredProvider)) {
            if (!StringUtils.hasText(bucket)) {
                bucket = ipfsProperties.getBucketName();
            }
        } else {
            if (!StringUtils.hasText(bucket) && minioProperties.getBuckets() != null) {
                bucket = minioProperties.getBuckets().get("content-share");
            }
            if (!StringUtils.hasText(bucket)) {
                bucket = minioProperties.getBucketName();
            }
        }

        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectName)) {
            return;
        }

        // 4. URL生成策略（按内容自身存储类型）
        String mediaUrl = null;
        try {
            if ("ipfs".equalsIgnoreCase(inferredProvider)) {
                log.info("使用IPFS存储生成URL: bucket={}, objectName={}", bucket, objectName);
                // objectName 可能是 CID，也可能是 key；IpfsService 会尝试解析 CID
                String cid = StringUtils.hasText(cidHint) ? cidHint : ipfsService.resolveCid(bucket, objectName);
                if (StringUtils.hasText(cid)) {
                    mediaUrl = ipfsService.generatePublicUrl(bucket, cid);
                } else {
                    mediaUrl = ipfsService.generatePublicUrl(bucket, objectName);
                }
            } else {
                log.info("使用MinIO存储生成URL: bucket={}, objectName={}", bucket, objectName);
                String publicEndpoint = minioProperties.getPublicEndpoint();
                if (StringUtils.hasText(publicEndpoint)) {
                    // 如果配置了公共访问端点，返回直接URL
                    mediaUrl = publicEndpoint + "/" + objectName;
                    log.info("使用MinIO公共访问URL: {}", mediaUrl);
                } else {
                    // 否则生成长期有效的预签名URL（24小时）
                    mediaUrl = minioService.getPresignedDownloadUrl(bucket, objectName, 1440);
                    log.info("使用MinIO预签名URL: {}", mediaUrl);
                }
            }
        } catch (Exception e) {
            log.warn("生成URL失败，保留原始mediaUrl: shareId={}, error={}", share.getId(), e.getMessage());
            return;
        }
        if (StringUtils.hasText(mediaUrl)) {
            share.setMediaUrl(mediaUrl);
        }
    }

    private String inferProvider(String storageProviderHint, String cidHint, String mediaUrl, String bucket) {
        // 1. 优先使用metadata中的storageProvider
        if (StringUtils.hasText(storageProviderHint)) {
            return storageProviderHint;
        }
        // 2. 如果有CID提示，肯定是IPFS
        if (StringUtils.hasText(cidHint)) {
            return "ipfs";
        }
        // 3. 从URL格式推断
        if (StringUtils.hasText(mediaUrl) && mediaUrl.contains("/ipfs/")) {
            return "ipfs";
        }
        // 4. 从bucket名称推断
        if (StringUtils.hasText(bucket) && StringUtils.hasText(ipfsProperties.getBucketName())
            && bucket.equalsIgnoreCase(ipfsProperties.getBucketName())) {
            return "ipfs";
        }
        // 5. 最后回退到全局配置的storage.provider
        String currentProvider = unifiedStorageService.getCurrentProvider();
        log.debug("使用全局配置的存储提供商: {}", currentProvider);
        return StringUtils.hasText(currentProvider) ? currentProvider : "minio";
    }

    private Map<Long, ContentPin> fetchActivePins(List<Long> shareIds) {
        List<ContentPin> pins = contentPinMapper.selectActivePinsByContentIds("CONTENT_SHARE", shareIds);
        if (CollectionUtils.isEmpty(pins)) {
            return Collections.emptyMap();
        }
        return pins.stream().collect(Collectors.toMap(
            ContentPin::getContentId,
            Function.identity(),
            (pin1, pin2) -> {
                LocalDateTime end1 = pin1.getEndTime();
                LocalDateTime end2 = pin2.getEndTime();
                if (end1 == null) {
                    return pin2;
                }
                if (end2 == null) {
                    return pin1;
                }
                return end1.isAfter(end2) ? pin1 : pin2;
            }
        ));
    }

    private Map<Long, BigDecimal> fetchTipTotals(List<Long> shareIds) {
        List<ContentTipSummary> summaries =
            contentTipMapper.selectTotalTipsByContentIds("CONTENT_SHARE", shareIds);
        if (CollectionUtils.isEmpty(summaries)) {
            return Collections.emptyMap();
        }
        return summaries.stream().collect(Collectors.toMap(
            ContentTipSummary::getContentId,
            summary -> summary.getTotalAmount() == null ? BigDecimal.ZERO : summary.getTotalAmount()
        ));
    }

    private Map<Long, String> fetchAuthorWallets(List<ContentShare> shares) {
        List<Long> userIds = shares.stream()
            .map(ContentShare::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        if (CollectionUtils.isEmpty(users)) {
            return Collections.emptyMap();
        }
        return users.stream()
            .filter(user -> StringUtils.hasText(user.getWalletAddress()))
            .collect(Collectors.toMap(
                User::getId,
                User::getWalletAddress,
                (addr1, addr2) -> addr1
            ));
    }

    private void validateRequest(CreateContentShareRequest request) {
        if (!"IMAGE".equalsIgnoreCase(request.getMediaType()) &&
            !"VIDEO".equalsIgnoreCase(request.getMediaType())) {
            throw new BusinessException("媒体类型仅支持IMAGE或VIDEO");
        }
        if (!StringUtils.hasText(request.getMediaUrl()) || !request.getMediaUrl().contains("/")) {
            throw new BusinessException("媒体存储路径不合法");
        }
    }

    /**
     * 监听内容奖励事件
     */
    @org.springframework.context.event.EventListener
    public void handleContentRewardEvent(ContentRewardEvent event) {
        log.info("📥 收到内容奖励事件: shareId={} (Rollup 模式下由批次发放)", event.getShareId());
    }
    
    /**
     * 获取当前登录用户的钱包地址
     */
    public String getCurrentUserWalletAddress() {
        try {
            // 从 Spring Security Context 获取当前用户ID
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            
            Object principal = auth.getPrincipal();
            if (principal == null) {
                return null;
            }
            
            Long userId = null;
            if (principal instanceof Long) {
                userId = (Long) principal;
            } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                if (user != null) {
                    userId = user.getId();
                }
            }
            
            if (userId == null) {
                return null;
            }
            
            User user = userMapper.selectById(userId);
            return user != null ? user.getWalletAddress() : null;
            
        } catch (Exception e) {
            log.error("获取当前用户钱包地址失败", e);
            return null;
        }
    }
}
