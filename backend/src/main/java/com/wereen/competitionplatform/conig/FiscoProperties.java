package com.wereen.competitionplatform.conig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FISCO BCOS 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "fisco")
public class FiscoProperties {

    /**
     * 配置文件路径
     */
    private String configFile;

    /**
     * 群组ID
     */
    private Integer groupId;

    /**
     * 合约地址
     */
    private String contractAddress;

    /**
     * 当前账户地址
     */
    private String currentAccount;
}
