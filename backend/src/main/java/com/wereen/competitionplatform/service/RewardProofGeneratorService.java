package com.wereen.competitionplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardProofGeneratorService {

    @Value("${reward.rollup.prover-cmd:}")
    private String proverCommand;

    @Value("${reward.rollup.prover-timeout-seconds:300}")
    private long timeoutSeconds;

    @Value("${reward.rollup.work-dir:}")
    private String rollupWorkDir;

    @Value("${blockchain.rollup.image-id:}")
    private String rollupImageId;

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private MockRewardProofGeneratorService mockProofGenerator;

    public void generateProofIfConfigured(Map<String, Object> metadata) {
        String proofFile = asString(metadata.get("proofFile"));
        if (!StringUtils.hasText(proofFile)) {
            log.warn("Rollup proof 文件路径缺失，跳过自动生成");
            return;
        }

        // 当 prover-cmd 未配置时，尝试使用 Mock 生成器（本地开发模式）
        if (!StringUtils.hasText(proverCommand)) {
            if (mockProofGenerator != null) {
                mockProofGenerator.generateMockProof(metadata, Path.of(proofFile));
            } else {
                log.info("Rollup proof 生成命令未配置，跳过自动生成");
            }
            return;
        }
        try {
            Path proofPath = Path.of(proofFile);
            Files.createDirectories(proofPath.getParent());

            Path inputPath = proofPath.resolveSibling(proofPath.getFileName() + ".json");
            Files.writeString(inputPath, objectMapper.writeValueAsString(metadata));

            ProcessBuilder builder = new ProcessBuilder(parseCommand(proverCommand));
            builder.redirectErrorStream(true);
            builder.environment().put("ROLLUP_METADATA", inputPath.toString());
            builder.environment().put("ROLLUP_PROOF_FILE", proofPath.toString());
            if (StringUtils.hasText(rollupImageId)) {
                builder.environment().put("ROLLUP_IMAGE_ID", rollupImageId);
            }
            if (StringUtils.hasText(rollupWorkDir)) {
                Path workDirPath = Path.of(rollupWorkDir);
                Files.createDirectories(workDirPath);
                builder.environment().put("RISC0_WORK_DIR", workDirPath.toString());
            }
            Process process = builder.start();

            StringBuilder output = new StringBuilder();
            Thread outputReader = new Thread(() -> readOutput(process, output));
            outputReader.setDaemon(true);
            outputReader.start();

            boolean finished = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Rollup proof 生成超时: {}", proofPath);
                return;
            }
            outputReader.join(TimeUnit.SECONDS.toMillis(1));
            if (process.exitValue() != 0) {
                String outputText = output.length() == 0 ? "<no-output>" : output.toString();
                log.warn("Rollup proof 生成失败: exitCode={}, output={}", process.exitValue(), outputText);
                return;
            }
            log.info("Rollup proof 生成完成: {}", proofPath);
        } catch (Exception e) {
            log.warn("Rollup proof 自动生成失败: {}", e.getMessage());
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String[] parseCommand(String command) {
        return command.trim().split("\\s+");
    }

    private void readOutput(Process process, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 8000) {
                    output.append("\n<truncated>");
                    break;
                }
                output.append(line).append('\n');
            }
        } catch (Exception e) {
            output.append("output-read-error: ").append(e.getMessage());
        }
    }
}
