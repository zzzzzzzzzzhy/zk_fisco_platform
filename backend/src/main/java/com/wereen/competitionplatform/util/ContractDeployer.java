package com.wereen.competitionplatform.util;

import com.wereen.competitionplatform.contracts.EvidenceContract;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;

/**
 * 智能合约部署工具
 */
@Slf4j
public class ContractDeployer {

    public static void main(String[] args) {
        String configFile = "config.toml";

        // 如果提供了配置文件路径参数
        if (args.length > 0) {
            configFile = args[0];
        }

        BcosSDK sdk = null;
        try {
            log.info("开始部署智能合约...");
            log.info("配置文件: {}", configFile);

            // 初始化SDK
            sdk = BcosSDK.build(configFile);

            // 创建客户端
            Client client = sdk.getClient(1);

            // 创建密钥对
            CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
            client.getCryptoSuite().setCryptoKeyPair(keyPair);

            log.info("部署账户地址: {}", keyPair.getAddress());

            // 部署合约
            EvidenceContract contract = EvidenceContract.deploy(client, keyPair);

            String contractAddress = contract.getContractAddress();

            log.info("========================================");
            log.info("合约部署成功！");
            log.info("合约地址: {}", contractAddress);
            log.info("部署账户: {}", keyPair.getAddress());
            log.info("========================================");
            log.info("");
            log.info("请将以下配置更新到 application.yml:");
            log.info("fisco:");
            log.info("  contract-address: {}", contractAddress);
            log.info("  current-account: {}", keyPair.getAddress());
            log.info("========================================");

        } catch (Exception e) {
            log.error("合约部署失败", e);
            System.exit(1);
        } finally {
            if (sdk != null) {
                sdk.stopAll();
            }
        }
    }
}
