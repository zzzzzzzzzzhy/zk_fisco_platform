package com.wereen.competitionplatform.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.wereen.competitionplatform.conig.MinioProperties;
import com.wereen.competitionplatform.exception.BusinessException;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;

/**
 * MinIO 文件存储服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建存储桶（如果不存在）
     */
    public void createBucketIfNotExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("创建存储桶成功: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("创建存储桶失败: {}", bucketName, e);
            throw new BusinessException("创建存储桶失败");
        }
    }

    /**
     * 上传文件
     */
    public String uploadFile(String bucketName, String objectName, MultipartFile file) {
        try {
            createBucketIfNotExists(bucketName);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("文件上传成功: {}/{}", bucketName, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("文件上传失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件上传失败");
        }
    }

    /**
     * 上传文件流
     */
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, long size, String contentType) {
        try {
            createBucketIfNotExists(bucketName);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );

            log.info("文件流上传成功: {}/{}", bucketName, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("文件流上传失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件流上传失败");
        }
    }

    /**
     * 获取文件预签名URL（用于直传）
     */
    public String getPresignedUploadUrl(String bucketName, String objectName, int expiryMinutes) {
        try {
            createBucketIfNotExists(bucketName);

            return getPresignClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取预签名上传URL失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("获取预签名上传URL失败");
        }
    }

    /**
     * 获取文件预签名下载URL（带Redis缓存）
     */
    public String getPresignedDownloadUrl(String bucketName, String objectName, int expiryMinutes) {
        try {
            // 生成缓存键
            String cacheKey = generateUrlCacheKey(bucketName, objectName, expiryMinutes);

            // 先从Redis缓存中获取
            String cachedUrl = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedUrl)) {
                log.debug("从缓存获取预签名URL: {}/{}", bucketName, objectName);
                return cachedUrl;
            }

            // 缓存未命中，生成新的预签名URL
            String presignedUrl = getPresignClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );

            // 缓存URL（有效期为请求有效期的80%）
            long cacheExpiryMinutes = (long) (expiryMinutes * 0.8);
            stringRedisTemplate.opsForValue().set(cacheKey, presignedUrl, cacheExpiryMinutes, TimeUnit.MINUTES);

            log.debug("生成并缓存预签名URL: {}/{}, 缓存时间: {}分钟", bucketName, objectName, cacheExpiryMinutes);
            return presignedUrl;

        } catch (Exception e) {
            log.error("获取预签名下载URL失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("获取预签名下载URL失败");
        }
    }

    /**
     * 生成URL缓存键
     */
    private String generateUrlCacheKey(String bucketName, String objectName, int expiryMinutes) {
        try {
            String keyData = bucketName + ":" + objectName + ":" + expiryMinutes;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(keyData.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return "minio:url:" + sb.toString();
        } catch (Exception e) {
            // 如果MD5生成失败，使用简单键
            return "minio:url:" + bucketName + ":" + objectName.hashCode();
        }
    }

    /**
     * 下载文件
     */
    public InputStream downloadFile(String bucketName, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件下载失败");
        }
    }

    /**
     * 删除文件
     */
    public void deleteFile(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: {}/{}", bucketName, objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}/{}", bucketName, objectName, e);
            throw new BusinessException("文件删除失败");
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String bucketName, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成预签名 URL 时优先使用对外地址，避免返回内网域名导致前端无法访问
     */
    private MinioClient getPresignClient() {
        if (StringUtils.hasText(minioProperties.getPublicEndpoint())) {
            return MinioClient.builder()
                    .endpoint(minioProperties.getPublicEndpoint())
                    .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                    .build();
        }
        return minioClient;
    }
}
