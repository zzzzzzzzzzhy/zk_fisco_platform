package com.wereen.competitionplatform;

import com.wereen.competitionplatform.service.PolygonContentProofService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PolygonIntegrationTest {

    @Autowired
    private PolygonContentProofService polygonContentProofService;

    @Test
    public void testPolygonContentProof() {
        try {
            System.out.println("开始测试Polygon存证功能...");

            // 测试参数
            Long shareId = System.currentTimeMillis();
            String dataHash = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456";
            String metadataJson = "{\"title\":\"测试内容\",\"userId\":1,\"test\":true,\"timestamp\":\"2025-11-14T03:00:00\"}";
            String publisherAddress = "0xeFdf04EbFD9DcFae3886A2d7E04B0bEdbe3E68f2";

            System.out.println("测试参数:");
            System.out.println("  ShareId: " + shareId);
            System.out.println("  DataHash: " + dataHash);
            System.out.println("  Publisher: " + publisherAddress);
            System.out.println("  Metadata: " + metadataJson);

            // 调用Polygon存证服务
            PolygonContentProofService.PolygonProofResult result = polygonContentProofService.recordContentShare(
                shareId, dataHash, metadataJson, publisherAddress);

            System.out.println("\n✅ Polygon存证成功!");
            System.out.println("交易哈希: " + result.getTxHash());
            System.out.println("区块号: " + result.getBlockNumber());
            System.out.println("区块时间: " + result.getBlockTime());
            System.out.println("发布者: " + result.getPublisher());

        } catch (Exception e) {
            System.err.println("❌ Polygon存证测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}