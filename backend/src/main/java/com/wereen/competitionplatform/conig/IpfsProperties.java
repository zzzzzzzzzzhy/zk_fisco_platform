package com.wereen.competitionplatform.conig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IPFS配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "ipfs")
public class IpfsProperties {

    /**
     * IPFS端点地址
     */
    private String endpoint;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 密钥
     */
    private String secretKey;

    /**
     * 桶名称
     */
    private String bucketName;

    /**
     * 区域
     */
    private String region;

    /**
     * 公共访问网关
     */
    private String publicGateway;
}