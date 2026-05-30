package com.wereen.competitionplatform.service;

import com.wereen.competitionplatform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 统一存储服务 - 根据配置选择MinIO或IPFS
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedStorageService {

    private final MinioService minioService;
    private final IpfsService ipfsService;

    @Value("${storage.provider:minio}")
    private String storageProvider;

    /**
     * 上传文件
     */
    public String uploadFile(String bucketName, String objectName, MultipartFile file) {
        if ("ipfs".equalsIgnoreCase(storageProvider)) {
            log.info("使用IPFS存储上传文件: {}/{}", bucketName, objectName);
            return ipfsService.uploadFile(bucketName, objectName, file);
        } else {
            log.info("使用MinIO存储上传文件: {}/{}", bucketName, objectName);
            return minioService.uploadFile(bucketName, objectName, file);
        }
    }

    /**
     * 上传文件流
     */
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, long size, String contentType) {
        if ("ipfs".equalsIgnoreCase(storageProvider)) {
            log.info("使用IPFS存储上传文件流: {}/{}", bucketName, objectName);
            try {
                byte[] bytes = inputStream.readAllBytes();
                return ipfsService.uploadFile(bucketName, objectName, bytes, contentType);
            } catch (Exception e) {
                log.error("IPFS文件流上传失败", e);
                throw new BusinessException("文件上传失败: " + e.getMessage());
            }
        } else {
            log.info("使用MinIO存储上传文件流: {}/{}", bucketName, objectName);
            return minioService.uploadFile(bucketName, objectName, inputStream, size, contentType);
        }
    }

    /**
     * 获取预签名下载URL
     */
    public String getPresignedDownloadUrl(String bucketName, String objectName, int expiryMinutes) {
        if ("ipfs".equalsIgnoreCase(storageProvider)) {
            log.debug("获取IPFS预签名URL: {}/{}", bucketName, objectName);
            return ipfsService.getPresignedDownloadUrl(bucketName, objectName, expiryMinutes);
        } else {
            log.debug("获取MinIO预签名URL: {}/{}", bucketName, objectName);
            return minioService.getPresignedDownloadUrl(bucketName, objectName, expiryMinutes);
        }
    }

    /**
     * 获取公共访问URL
     */
    public String getPublicUrl(String bucketName, String objectName) {
        if ("ipfs".equalsIgnoreCase(storageProvider)) {
            log.debug("获取IPFS公共URL: {}/{}", bucketName, objectName);
            String cid = ipfsService.resolveCid(bucketName, objectName);
            if (cid != null && !cid.isEmpty()) {
                return ipfsService.generatePublicUrl(bucketName, cid);
            }
            return ipfsService.generatePublicUrl(bucketName, objectName);
        } else {
            log.debug("获取MinIO公共URL: {}/{}", bucketName, objectName);
            // MinIO的公共URL逻辑可以在这里实现
            return getPresignedDownloadUrl(bucketName, objectName, 1440); // 24小时
        }
    }

    /**
     * 下载文件
     */
    public InputStream downloadFile(String bucketName, String objectName) {
        if ("ipfs".equalsIgnoreCase(storageProvider)) {
            log.debug("从IPFS下载文件: {}/{}", bucketName, objectName);
            return ipfsService.downloadFile(bucketName, objectName);
        } else {
            log.debug("从MinIO下载文件: {}/{}", bucketName, objectName);
            return minioService.downloadFile(bucketName, objectName);
        }
    }

    /**
     * 删除文件
     */
    public void deleteFile(String bucketName, String objectName) {
        if ("ipfs".equalsIgnoreCase(storageProvider)) {
            log.info("从IPFS删除文件: {}/{}", bucketName, objectName);
            ipfsService.deleteFile(bucketName, objectName);
        } else {
            log.info("从MinIO删除文件: {}/{}", bucketName, objectName);
            minioService.deleteFile(bucketName, objectName);
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String bucketName, String objectName) {
        if ("ipfs".equalsIgnoreCase(storageProvider)) {
            log.debug("检查IPFS文件存在性: {}/{}", bucketName, objectName);
            return ipfsService.fileExists(bucketName, objectName);
        } else {
            log.debug("检查MinIO文件存在性: {}/{}", bucketName, objectName);
            return minioService.fileExists(bucketName, objectName);
        }
    }

    /**
     * 获取当前存储提供商
     */
    public String getCurrentProvider() {
        return storageProvider;
    }

    /**
     * 检查是否使用IPFS
     */
    public boolean isUsingIpfs() {
        return "ipfs".equalsIgnoreCase(storageProvider);
    }

    /**
     * 检查是否使用MinIO
     */
    public boolean isUsingMinio() {
        return "minio".equalsIgnoreCase(storageProvider) || storageProvider == null;
    }
}
