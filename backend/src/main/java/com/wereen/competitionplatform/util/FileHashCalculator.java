package com.wereen.competitionplatform.util;

import com.wereen.competitionplatform.exception.FilePrecheckException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件哈希计算工具类 - 支持分块流式计算
 */
@Slf4j
public class FileHashCalculator {

    /**
     * 计算文件哈希值（流式计算，避免大文件OOM）
     *
     * @param inputStream 文件输入流
     * @param algorithm   哈希算法（SHA-256/SHA-512/SHA3-256/MD5）
     * @param bufferSize  分块大小（字节）
     * @return 十六进制哈希字符串
     */
    public static String calculateHash(InputStream inputStream, String algorithm, int bufferSize) {
        try {
            MessageDigest digest = MessageDigest.getInstance(normalizeAlgorithm(algorithm));
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            long totalBytes = 0;

            long startTime = System.currentTimeMillis();

            // 分块读取并计算哈希
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            long endTime = System.currentTimeMillis();
            log.info("哈希计算完成: 算法={}, 文件大小={}MB, 耗时={}ms",
                    algorithm, totalBytes / 1024 / 1024, endTime - startTime);

            // 获取哈希值并转换为十六进制字符串
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            log.error("不支持的哈希算法: {}", algorithm, e);
            throw new FilePrecheckException(FilePrecheckException.ErrorCode.HASH_FAILED,
                    "不支持的哈希算法: " + algorithm);
        } catch (IOException e) {
            log.error("读取文件失败", e);
            throw new FilePrecheckException(FilePrecheckException.ErrorCode.HASH_FAILED,
                    "文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 使用默认分块大小计算哈希（8KB）
     */
    public static String calculateHash(InputStream inputStream, String algorithm) {
        return calculateHash(inputStream, algorithm, 8192);
    }

    /**
     * 计算SHA-256哈希（推荐）
     */
    public static String calculateSHA256(InputStream inputStream) {
        return calculateHash(inputStream, "SHA-256");
    }

    /**
     * 计算SHA-256哈希（自定义分块大小）
     */
    public static String calculateSHA256(InputStream inputStream, int bufferSize) {
        return calculateHash(inputStream, "SHA-256", bufferSize);
    }

    /**
     * 计算SHA3-256哈希
     */
    public static String calculateSHA3_256(InputStream inputStream) {
        return calculateHash(inputStream, "SHA3-256");
    }

    /**
     * 计算MD5哈希（不推荐用于安全场景）
     */
    public static String calculateMD5(InputStream inputStream) {
        return calculateHash(inputStream, "MD5");
    }

    /**
     * 标准化算法名称
     */
    private static String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return "SHA-256";
        }

        // 移除连字符和下划线，统一大小写
        String normalized = algorithm.replace("-", "").replace("_", "").toUpperCase();

        switch (normalized) {
            case "SHA256":
                return "SHA-256";
            case "SHA512":
                return "SHA-512";
            case "SHA3256":
            case "SHA3":
                return "SHA3-256";
            case "SHA3512":
                return "SHA3-512";
            case "MD5":
                return "MD5";
            default:
                return algorithm; // 保持原样，让MessageDigest抛出异常
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 验证哈希值是否匹配
     *
     * @param inputStream  文件输入流
     * @param algorithm    哈希算法
     * @param expectedHash 预期哈希值
     * @return 是否匹配
     */
    public static boolean verifyHash(InputStream inputStream, String algorithm, String expectedHash) {
        String actualHash = calculateHash(inputStream, algorithm);
        return actualHash.equalsIgnoreCase(expectedHash);
    }
}
