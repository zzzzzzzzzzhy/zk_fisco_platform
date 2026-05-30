package com.wereen.competitionplatform.conig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MinIO 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * MinIO 服务地址
     */
    private String endpoint;

    /**
     * 对外可访问的 MinIO 地址（用于生成给前端使用的预签名 URL）
     * 不配置时默认使用 endpoint
     */
    private String publicEndpoint;

    /**
     * MinIO 控制台地址
     */
    private String consoleUrl;

    /**
     * 访问用户名
     */
    private String accessKey;

    /**
     * 访问密码
     */
    private String secretKey;

    /**
     * 默认存储桶名称
     */
    private String bucketName;

    /**
     * 存储桶配置
     */
    private Map<String, String> buckets;
}
