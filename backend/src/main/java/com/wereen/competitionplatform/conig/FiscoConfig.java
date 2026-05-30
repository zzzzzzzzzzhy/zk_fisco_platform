package com.wereen.competitionplatform.conig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * FISCO BCOS Configuration
 * Based on FISCO BCOS Java SDK 2.9.1 (compatible with FISCO BCOS 2.x nodes)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    prefix = "fisco",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class FiscoConfig {

    private final FiscoProperties fiscoProperties;

    @Bean
    public BcosSDK bcosSDK() {
        try {
            String configFile = fiscoProperties.getConfigFile();
            log.info("Initializing FISCO BCOS SDK, config file from properties: {}", configFile);
            
            // 检查配置文件是否为空
            if (configFile == null || configFile.isEmpty()) {
                throw new IllegalStateException("FISCO config file path is not set");
            }

            // 支持绝对路径（容器挂载）或 classpath 相对路径
            String configFilePath;
            if (configFile.startsWith("/") || configFile.startsWith("file:")) {
                // 绝对路径：直接使用（容器挂载场景）
                configFilePath = configFile.replace("file:", "");
                log.info("Using absolute path for config file: {}", configFilePath);
                
                // 验证文件是否存在
                java.io.File file = new java.io.File(configFilePath);
                if (!file.exists()) {
                    throw new IllegalStateException("Config file does not exist: " + configFilePath);
                }
                log.info("Config file verified, exists at: {}", file.getAbsolutePath());
            } else {
                // 相对路径：从 classpath 加载（传统方式）
                java.net.URL resource = this.getClass().getClassLoader().getResource(configFile);
                if (resource == null) {
                    throw new IllegalStateException("Config file not found in classpath: " + configFile);
                }
                configFilePath = resource.getPath();
                log.info("Config file resolved from classpath to: {}", configFilePath);
            }

            log.info("Building FISCO BCOS SDK with config file: {}", configFilePath);
            BcosSDK sdk = BcosSDK.build(configFilePath);
            log.info("FISCO BCOS SDK initialized successfully");
            return sdk;
        } catch (Exception e) {
            log.error("Failed to initialize FISCO BCOS SDK", e);
            throw new RuntimeException("Failed to initialize FISCO BCOS SDK", e);
        }
    }

    @Bean
    public Client fiscoBcosClient(BcosSDK bcosSDK) {
        try {
            Integer groupId = fiscoProperties.getGroupId();
            log.info("Creating FISCO BCOS Client, group ID: {}", groupId);

            Client client = bcosSDK.getClient(groupId);

            // Log the loaded account
            CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
            log.info("Using account address: {}", keyPair.getAddress());

            // Test connection
            log.info("FISCO BCOS Client initialized successfully, group ID: {}", groupId);
            log.info("Current block number: {}", client.getBlockNumber());

            return client;
        } catch (Exception e) {
            log.error("Failed to create FISCO BCOS Client", e);
            throw new RuntimeException("Failed to create FISCO BCOS Client", e);
        }
    }

    @Bean
    public CryptoKeyPair cryptoKeyPair(Client fiscoBcosClient) {
        // Return the keypair that was already loaded by the SDK from config.toml
        return fiscoBcosClient.getCryptoSuite().getCryptoKeyPair();
    }
}
