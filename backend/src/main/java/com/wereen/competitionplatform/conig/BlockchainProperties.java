package com.wereen.competitionplatform.conig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 区块链相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {

    private Polygon polygon = new Polygon();
    private Admin admin = new Admin();
    private ContentShare contentShare = new ContentShare();

    @Data
    public static class Polygon {
        private String rpcUrl;
        private Long chainId;
    }

    @Data
    public static class Admin {
        private String privateKey;
    }

    @Data
    public static class ContentShare {
        /**
         * Polygon上用于记录内容分享的合约地址
         */
        private String registryAddress;

        // 设置器确保地址格式正确
        public void setRegistryAddress(String registryAddress) {
            this.registryAddress = registryAddress;
        }
    }
}
