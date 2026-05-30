package com.wereen.competitionplatform.util;

import com.wereen.competitionplatform.exception.FilePrecheckException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件内容验证工具类
 */
@Slf4j
public class FileContentValidator {

    /**
     * 验证ZIP压缩包
     *
     * @param inputStream         文件输入流
     * @param maxFiles            最大文件数量
     * @param maxDepth            最大嵌套深度
     * @param maxDecompressedSize 最大解压后大小（字节）
     */
    public static void validateZipArchive(InputStream inputStream, int maxFiles, int maxDepth, long maxDecompressedSize) {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            int fileCount = 0;
            long totalSize = 0;
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                fileCount++;

                // 检查文件数量限制
                if (fileCount > maxFiles) {
                    throw new FilePrecheckException(
                            FilePrecheckException.ErrorCode.CORRUPTED_ARCHIVE,
                            String.format("压缩包文件数量超出限制（%d > %d）", fileCount, maxFiles)
                    );
                }

                String entryName = entry.getName();

                // 检查路径穿越攻击
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\")) {
                    throw new FilePrecheckException(
                            FilePrecheckException.ErrorCode.MALICIOUS_CONTENT,
                            "压缩包包含非法路径: " + entryName
                    );
                }

                // 检查嵌套深度
                int depth = countPathDepth(entryName);
                if (depth > maxDepth) {
                    throw new FilePrecheckException(
                            FilePrecheckException.ErrorCode.MALICIOUS_CONTENT,
                            String.format("压缩包嵌套深度超出限制（%d > %d）: %s", depth, maxDepth, entryName)
                    );
                }

                // 检查文件名长度
                if (entryName.length() > 255) {
                    throw new FilePrecheckException(
                            FilePrecheckException.ErrorCode.MALICIOUS_CONTENT,
                            "文件名过长: " + entryName.substring(0, 50) + "..."
                    );
                }

                // 检查解压后大小（防止ZIP炸弹）
                if (!entry.isDirectory()) {
                    long size = entry.getSize();
                    if (size > 0) {
                        totalSize += size;
                        if (totalSize > maxDecompressedSize) {
                            throw new FilePrecheckException(
                                    FilePrecheckException.ErrorCode.SIZE_EXCEEDED,
                                    String.format("解压后大小超出限制（%dMB > %dMB）",
                                            totalSize / 1024 / 1024, maxDecompressedSize / 1024 / 1024)
                            );
                        }
                    } else {
                        // size为-1时，需要实际读取来检测
                        totalSize += validateEntrySize(zipInputStream, maxDecompressedSize - totalSize);
                    }
                }

                zipInputStream.closeEntry();
            }

            log.info("ZIP压缩包验证通过: 文件数={}, 解压后大小={}MB", fileCount, totalSize / 1024 / 1024);

        } catch (IOException e) {
            log.error("ZIP压缩包验证失败", e);
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.CORRUPTED_ARCHIVE,
                    "压缩包损坏或无法解压: " + e.getMessage()
            );
        }
    }

    /**
     * 验证单个ZIP条目的大小（读取内容时检测）
     */
    private static long validateEntrySize(ZipInputStream zipInputStream, long remainingSize) throws IOException {
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        int bytesRead;

        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
            totalRead += bytesRead;
            if (totalRead > remainingSize) {
                throw new FilePrecheckException(
                        FilePrecheckException.ErrorCode.SIZE_EXCEEDED,
                        "解压后大小超出限制"
                );
            }
        }

        return totalRead;
    }

    /**
     * 计算路径深度
     */
    private static int countPathDepth(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        int depth = 1;
        for (char c : path.toCharArray()) {
            if (c == '/' || c == '\\') {
                depth++;
            }
        }
        return depth;
    }

    /**
     * 验证文件名安全性
     */
    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.INVALID_FORMAT,
                    "文件名为空"
            );
        }

        // 检查文件名长度
        if (fileName.length() > 255) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.INVALID_FORMAT,
                    "文件名过长（最大255字符）"
            );
        }

        // 检查非法字符
        String illegalChars = "<>:\"|?*";
        for (char c : illegalChars.toCharArray()) {
            if (fileName.indexOf(c) != -1) {
                throw new FilePrecheckException(
                        FilePrecheckException.ErrorCode.INVALID_FORMAT,
                        "文件名包含非法字符: " + c
                );
            }
        }

        // 检查路径穿越
        if (fileName.contains("..") || fileName.startsWith("/") || fileName.startsWith("\\")) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.MALICIOUS_CONTENT,
                    "文件名包含非法路径"
            );
        }
    }

    /**
     * 检测压缩比（防ZIP炸弹）
     *
     * @param compressedSize   压缩后大小
     * @param decompressedSize 解压后大小
     * @param maxRatio         最大压缩比
     */
    public static void validateCompressionRatio(long compressedSize, long decompressedSize, int maxRatio) {
        if (compressedSize <= 0) {
            return; // 无法计算压缩比
        }

        long ratio = decompressedSize / compressedSize;
        if (ratio > maxRatio) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.MALICIOUS_CONTENT,
                    String.format("压缩比异常，疑似ZIP炸弹（%d倍 > %d倍）", ratio, maxRatio)
            );
        }
    }
}
