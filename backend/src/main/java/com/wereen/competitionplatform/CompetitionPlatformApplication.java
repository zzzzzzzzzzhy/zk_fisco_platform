package com.wereen.competitionplatform;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 竞赛平台主应用
 */
@Slf4j
@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties
@MapperScan("com.wereen.competitionplatform.mapper")
public class CompetitionPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompetitionPlatformApplication.class, args);
        log.info("========================================");
        log.info("竞赛平台启动成功！");
        log.info("API 地址: http://localhost:8080/api");
        log.info("========================================");
    }

}
