package com.wereen.competitionplatform;

import com.wereen.competitionplatform.service.RewardEventRollupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@SpringBootApplication
@ComponentScan
public class ManualRollupTest implements CommandLineRunner {

    @Autowired
    private RewardEventRollupService rollupService;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ManualRollupTest.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n========================================");
        System.out.println("  手动触发 Rollup 聚合测试");
        System.out.println("========================================\n");

        // 定义时间窗口（过去4小时到现在）
        LocalDateTime windowEnd = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime windowStart = windowEnd.minusHours(4);

        System.out.println("时间窗口:");
        System.out.println("  开始: " + windowStart);
        System.out.println("  结束: " + windowEnd);
        System.out.println();

        // 分别聚合不同类型的事件
        System.out.println("1. 聚合 CHECKIN 事件...");
        rollupService.rollupEvents("CHECKIN", 3, "CHECKIN_ROLLUP", windowStart, windowEnd);

        System.out.println("\n2. 聚合 CONTENT_SHARE 事件...");
        rollupService.rollupEvents("CONTENT_SHARE", 1, "CONTENT_SHARE_ROLLUP", windowStart, windowEnd);

        System.out.println("\n3. 聚合 COMMENT 事件...");
        rollupService.rollupEvents("COMMENT", 2, "COMMENT_ROLLUP", windowStart, windowEnd);

        System.out.println("\n========================================");
        System.out.println("  Rollup 聚合完成！");
        System.out.println("========================================\n");

        System.out.println("下一步：");
        System.out.println("  1. 查看 chain_proofs 表，检查批次记录");
        System.out.println("  2. 查看 proofs/rollup/ 目录，检查证明文件");
        System.out.println("  3. 运行提交脚本：bash scripts/submit-rollup.sh");
    }
}
