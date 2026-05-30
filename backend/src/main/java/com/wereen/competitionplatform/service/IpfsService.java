package com.wereen.competitionplatform.service;

import com.wereen.competitionplatform.conig.IpfsProperties;
import com.wereen.competitionplatform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * IPFS/4Everland 文件存储服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpfsService {

    private final IpfsProperties ipfsProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private volatile S3Client s3Client;
    private volatile boolean s3ClientPathStyle = true;

    private synchronized void rebuildS3Client(boolean pathStyle) {
        try {
            String accessKey = sanitizeEnvValue(ipfsProperties.getAccessKey());
            String secretKey = sanitizeEnvValue(ipfsProperties.getSecretKey());
            if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
                throw new BusinessException("IPFS 访问密钥未配置，请设置 IPFS_ACCESS_KEY/IPFS_SECRET_KEY");
            }
            String endpoint = sanitizeEnvValue(ipfsProperties.getEndpoint());
            String region = sanitizeEnvValue(ipfsProperties.getRegion());
            region = normalizeRegion(endpoint, region);
            // 去掉末尾的斜杠，避免某些 S3 兼容服务签名计算差异
            if (endpoint != null && endpoint.endsWith("/")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }

            S3Client old = s3Client;
            if (old != null) {
                try {
                    old.close();
                } catch (Exception ignore) {
                }
            }

            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.of(region))
                    .serviceConfiguration(S3Configuration.builder()
                            // 兼容不同 S3 服务：有些只支持 path-style / virtual-host-style 中一种
                            .pathStyleAccessEnabled(pathStyle)
                            // 某些 S3 兼容服务对分块上传/签名支持不完整，禁用 chunked encoding 规避签名不匹配
                            .chunkedEncodingEnabled(false)
                            // 4Everland 等 S3 兼容服务可能不按 AWS 规范返回校验和头，禁用校验和验证避免上传报错
                            .checksumValidationEnabled(false)
                            .build())
                    .build();
            s3ClientPathStyle = pathStyle;

            log.info("IPFS S3客户端初始化成功: endpoint={}, region={}, pathStyle={}", endpoint, region, pathStyle);
        } catch (Exception e) {
            log.error("IPFS S3客户端初始化失败", e);
            throw new BusinessException("IPFS服务初始化失败: " + e.getMessage());
        }
    }

    private boolean isSignatureMismatch(Exception e) {
        if (e == null) return false;
        if (e instanceof S3Exception s3e) {
            String code = null;
            try {
                if (s3e.awsErrorDetails() != null) {
                    code = s3e.awsErrorDetails().errorCode();
                }
            } catch (Exception ignore) {
            }
            String msg = s3e.getMessage() != null ? s3e.getMessage() : "";
            return "SignatureDoesNotMatch".equalsIgnoreCase(code)
                    || msg.contains("SignatureDoesNotMatch")
                    || msg.contains("The request signature we calculated does not match");
        }
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return msg.contains("SignatureDoesNotMatch")
                || msg.contains("The request signature we calculated does not match");
    }

    private <T> T withSignatureFallback(java.util.concurrent.Callable<T> action) throws Exception {
        try {
            return action.call();
        } catch (Exception first) {
            if (!isSignatureMismatch(first)) {
                throw first;
            }
            boolean nextPathStyle = !s3ClientPathStyle;
            log.warn("检测到 S3 Signature 不匹配，尝试切换 pathStyle 并重试: from={} to={}", s3ClientPathStyle, nextPathStyle);
            rebuildS3Client(nextPathStyle);
            return action.call();
        }
    }

    /**
     * 初始化S3客户端
     */
    private S3Client getS3Client() {
        if (s3Client == null) {
            synchronized (this) {
                if (s3Client == null) {
                    rebuildS3Client(true);
                }
            }
        }
        return s3Client;
    }

    /**
     * 创建存储桶（如果不存在）
     */
    public void createBucketIfNotExists(String bucketName) {
        try {
            boolean bucketExists = withSignatureFallback(() -> {
                S3Client client = getS3Client();
                return client.headBucket(HeadBucketRequest.builder()
                                .bucket(bucketName)
                                .build())
                        .sdkHttpResponse()
                        .isSuccessful();
            });

            if (!bucketExists) {
                withSignatureFallback(() -> {
                    S3Client client = getS3Client();
                    client.createBucket(CreateBucketRequest.builder()
                            .bucket(bucketName)
                            .build());
                    return null;
                });
                log.info("IPFS存储桶创建成功: {}", bucketName);
            }
        } catch (NoSuchBucketException e) {
            log.info("IPFS存储桶不存在，准备创建: {}", bucketName);
            try {
                withSignatureFallback(() -> {
                    S3Client client = getS3Client();
                    client.createBucket(CreateBucketRequest.builder()
                            .bucket(bucketName)
                            .build());
                    return null;
                });
                log.info("IPFS存储桶创建成功: {}", bucketName);
            } catch (Exception ex) {
                log.error("创建IPFS存储桶失败: {}", bucketName, ex);
                throw new BusinessException("创建存储桶失败");
            }
        } catch (Exception e) {
            log.warn("检查IPFS存储桶失败: {}", bucketName, e);
        }
    }

    /**
     * 上传文件到IPFS
     */
    public String uploadFile(String bucketName, String objectName, MultipartFile file) {
        try {
            createBucketIfNotExists(bucketName);

            // 上传文件到IPFS
            PutObjectResponse response = withSignatureFallback(() -> {
                S3Client client = getS3Client();
                return client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectName)
                                .contentType(file.getContentType())
                                .build(),
                        RequestBody.fromBytes(file.getBytes())
                );
            });

            String cid = normalizeCidFromEtag(response.eTag());
            if (StringUtils.hasText(cid)) {
                cacheCid(bucketName, objectName, cid);
            }
            log.info("IPFS文件上传成功: {}/{} - CID: {}", bucketName, objectName, cid);

            // 4Everland 的 eTag 通常就是 CID，优先返回 CID 网关 URL
            if (StringUtils.hasText(cid)) {
                return generatePublicUrl(bucketName, cid);
            }
            // 兜底：如果拿不到 CID，则尝试解析或返回兼容 URL
            return generateIpfsUrl(bucketName, objectName);
        } catch (IOException e) {
            log.error("读取文件失败", e);
            throw new BusinessException("读取文件失败");
        } catch (Exception e) {
            log.error("IPFS文件上传失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件流到IPFS
     */
    public String uploadFile(String bucketName, String objectName, byte[] fileBytes, String contentType) {
        try {
            createBucketIfNotExists(bucketName);

            PutObjectResponse response = withSignatureFallback(() -> {
                S3Client client = getS3Client();
                return client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectName)
                                .contentType(contentType)
                                .build(),
                        RequestBody.fromBytes(fileBytes)
                );
            });

            String cid = normalizeCidFromEtag(response.eTag());
            if (StringUtils.hasText(cid)) {
                cacheCid(bucketName, objectName, cid);
            }
            log.info("IPFS文件流上传成功: {}/{} - CID: {}", bucketName, objectName, cid);

            if (StringUtils.hasText(cid)) {
                return generatePublicUrl(bucketName, cid);
            }
            return generateIpfsUrl(bucketName, objectName);
        } catch (Exception e) {
            log.error("IPFS文件流上传失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 生成预签名下载URL
     */
    public String getPresignedDownloadUrl(String bucketName, String objectName, int expiryMinutes) {
        try {
            String cacheKey = generateUrlCacheKey(bucketName, objectName, expiryMinutes);

            // 检查Redis缓存
            String cachedUrl = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedUrl != null) {
                log.debug("从缓存获取IPFS预签名URL: {}/{}", bucketName, objectName);
                return cachedUrl;
            }

            S3Client client = getS3Client();

            // 4Everland IPFS不支持预签名URL，直接返回公共URL（使用 CID）
            String cid = resolveCid(bucketName, objectName);
            String presignedUrl = StringUtils.hasText(cid)
                ? generatePublicUrl(bucketName, cid)
                : generatePublicUrl(bucketName, objectName);

            // 缓存URL（有效期为请求有效期的80%）
            long cacheExpiryMinutes = (long) (expiryMinutes * 0.8);
            stringRedisTemplate.opsForValue().set(cacheKey, presignedUrl, cacheExpiryMinutes, java.util.concurrent.TimeUnit.MINUTES);

            log.debug("生成IPFS预签名URL: {}/{}", bucketName, objectName);
            return presignedUrl;

        } catch (Exception e) {
            log.error("获取IPFS预签名URL失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("获取下载链接失败");
        }
    }

    /**
     * 生成IPFS公共访问URL
     */
    public String generatePublicUrl(String bucketName, String objectName) {
        // 4Everland的公共访问格式
        return ipfsProperties.getPublicGateway() + "/ipfs/" + objectName;
    }

    /**
     * 生成IPFS URL
     */
    private String generateIpfsUrl(String bucketName, String objectName) {
        // 对于4Everland，eTag 通常是 CID；如果 objectName 本身就是 CID，直接走网关
        if (isCid(objectName)) {
            return generatePublicUrl(bucketName, objectName);
        }
        String cid = resolveCid(bucketName, objectName);
        if (StringUtils.hasText(cid)) {
            return generatePublicUrl(bucketName, cid);
        }
        // 兜底：仍然返回兼容 URL
        return generatePublicUrl(bucketName, objectName);
    }

    /**
     * 生成URL缓存键
     */
    private String generateUrlCacheKey(String bucketName, String objectName, int expiryMinutes) {
        return "ipfs:url:" + bucketName + ":" + objectName + ":" + expiryMinutes;
    }

    private String cidCacheKey(String bucketName, String objectName) {
        return "ipfs:cid:" + bucketName + ":" + objectName;
    }

    private void cacheCid(String bucketName, String objectName, String cid) {
        try {
            // CID 基本稳定，缓存 30 天
            stringRedisTemplate.opsForValue().set(cidCacheKey(bucketName, objectName), cid, Duration.ofDays(30));
        } catch (Exception ignore) {
        }
    }

    private String normalizeCidFromEtag(String etag) {
        if (!StringUtils.hasText(etag)) {
            return null;
        }
        String value = etag.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return isCid(value) ? value : null;
    }

    private boolean isCid(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.startsWith("Qm") || value.startsWith("baf");
    }

    /**
     * 通过 S3 HeadObject 解析 CID（4Everland 通常把 CID 放在 eTag）
     */
    public String resolveCid(String bucketName, String objectName) {
        if (isCid(objectName)) {
            return objectName;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(cidCacheKey(bucketName, objectName));
            if (StringUtils.hasText(cached) && isCid(cached)) {
                return cached;
            }
        } catch (Exception ignore) {
        }
        try {
            HeadObjectResponse resp = withSignatureFallback(() -> {
                S3Client client = getS3Client();
                return client.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectName)
                        .build());
            });
            String cid = normalizeCidFromEtag(resp.eTag());
            if (StringUtils.hasText(cid)) {
                cacheCid(bucketName, objectName, cid);
            }
            return cid;
        } catch (Exception e) {
            log.debug("解析 CID 失败: {}/{} - {}", bucketName, objectName, e.getMessage());
            return null;
        }
    }

    /**
     * 通过 S3 GetObject 下载文件（用于后端计算哈希等）
     */
    public InputStream downloadFile(String bucketName, String objectName) {
        try {
            return withSignatureFallback(() -> {
                S3Client client = getS3Client();
                return client.getObject(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectName)
                        .build());
            });
        } catch (Exception e) {
            log.error("IPFS文件下载失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    private String sanitizeEnvValue(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        // 兼容 .env 里被误写成引号包裹的情况
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            if (v.length() >= 2) {
                v = v.substring(1, v.length() - 1).trim();
            }
        }
        return v;
    }

    private String normalizeRegion(String endpoint, String region) {
        String value = sanitizeEnvValue(region);
        if (!StringUtils.hasText(value)) {
            value = "us-east-1";
        }
        // 兼容某些文档里写的 auto；4Everland 的 S3 兼容接口通常需要具体 region 才能通过签名校验
        if ("auto".equalsIgnoreCase(value)) {
            if (endpoint != null && endpoint.contains("4everland")) {
                value = "us-east-1";
            }
        }
        return value;
    }

    /**
     * 删除文件
     */
    public void deleteFile(String bucketName, String objectName) {
        try {
            withSignatureFallback(() -> {
                S3Client client = getS3Client();
                client.deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectName)
                                .build()
                );
                return null;
            });
            log.info("IPFS文件删除成功: {}/{}", bucketName, objectName);
        } catch (Exception e) {
            log.error("IPFS文件删除失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件删除失败");
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String bucketName, String objectName) {
        try {
            withSignatureFallback(() -> {
                S3Client client = getS3Client();
                client.headObject(
                        HeadObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectName)
                                .build()
                );
                return null;
            });
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("检查IPFS文件存在性失败: {}/{}", bucketName, objectName, e);
            return false;
        }
    }
}
