package com.wereen.competitionplatform.consumer;

import com.wereen.competitionplatform.conig.FilePrecheckProperties;
import com.wereen.competitionplatform.exception.FilePrecheckException;
import com.wereen.competitionplatform.service.MinioService;
import com.wereen.competitionplatform.service.SubmissionService;
import com.wereen.competitionplatform.util.FileContentValidator;
import com.wereen.competitionplatform.util.FileHashCalculator;
import com.wereen.competitionplatform.util.FileTypeDetector;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * 上传预检消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadPrecheckConsumer implements StreamListener<String, ObjectRecord<String, Map<String, String>>> {

    private final SubmissionService submissionService;
    private final MinioService minioService;
    private final MinioClient minioClient;
    private final FilePrecheckProperties precheckProperties;

    @Override
    public void onMessage(ObjectRecord<String, Map<String, String>> record) {
        Long submissionId = null;
        try {
            Map<String, String> taskData = record.getValue();
            submissionId = Long.parseLong(taskData.get("submissionId"));
            String filePath = taskData.get("filePath");

            log.info("处理上传预检任务: submissionId={}, filePath={}", submissionId, filePath);

            // 检查预检是否启用
            if (!precheckProperties.getEnabled()) {
                log.info("文件预检已禁用，跳过预检");
                submissionService.updatePrecheckStatus(submissionId, 2, null);
                return;
            }

            // 更新状态为检查中
            submissionService.updatePrecheckStatus(submissionId, 1, null);

            // 执行预检流程
            performPrecheck(submissionId, filePath);

            // 更新预检状态为通过
            submissionService.updatePrecheckStatus(submissionId, 2, null);

            log.info("上传预检任务完成: submissionId={}", submissionId);

        } catch (FilePrecheckException e) {
            log.error("文件预检失败: submissionId={}, errorCode={}, message={}",
                    submissionId, e.getErrorCode(), e.getMessage());
            if (submissionId != null) {
                submissionService.updatePrecheckStatus(submissionId, 3, e.getMessage());
            }
        } catch (Exception e) {
            log.error("处理上传预检任务失败: submissionId={}", submissionId, e);
            if (submissionId != null) {
                submissionService.updatePrecheckStatus(submissionId, 3, "系统错误: " + e.getMessage());
            }
        }
    }

    /**
     * 执行文件预检
     */
    private void performPrecheck(Long submissionId, String filePath) {
        // 解析存储桶和对象名
        String bucketName = extractBucketName(filePath);
        String objectName = extractObjectName(filePath);

        log.debug("开始预检: bucket={}, object={}", bucketName, objectName);

        // 1. 文件存在性验证
        validateFileExists(bucketName, objectName);

        // 2. 获取文件元数据
        StatObjectResponse objectStat = getObjectMetadata(bucketName, objectName);
        long fileSize = objectStat.size();
        String contentType = objectStat.contentType();

        log.info("文件元数据: size={}MB, contentType={}", fileSize / 1024 / 1024, contentType);

        // 3. 文件大小验证
        validateFileSize(fileSize);

        // 4. 文件扩展名验证
        String fileName = objectName.substring(objectName.lastIndexOf('/') + 1);
        String extension = FileTypeDetector.getFileExtension(fileName);
        validateFileExtension(extension);

        // 5. 文件名安全性验证
        FileContentValidator.validateFileName(fileName);

        // 6. 文件格式验证（魔数验证）+ 7. 哈希计算
        String fileHash;
        try (InputStream inputStream = minioService.downloadFile(bucketName, objectName)) {
            // 先标记流，以便重置
            inputStream.mark(Integer.MAX_VALUE);

            // 魔数验证
            if (!FileTypeDetector.validateFileType(inputStream, extension)) {
                log.warn("文件格式与扩展名不匹配: extension={}", extension);
                // 注意：对于文本文件，魔数验证可能失败，这是正常的
            }

            // 重置流以便计算哈希
            inputStream.reset();

            // 计算文件哈希
            fileHash = calculateFileHash(inputStream);
            log.info("文件哈希计算完成: algorithm={}, hash={}", precheckProperties.getHashAlgorithm(), fileHash);
        } catch (Exception e) {
            throw new FilePrecheckException(FilePrecheckException.ErrorCode.HASH_FAILED,
                    "文件读取或哈希计算失败: " + e.getMessage(), e);
        }

        // 8. 文件内容验证（针对压缩包）
        if (isArchiveFile(extension)) {
            validateArchiveContent(bucketName, objectName, fileSize);
        }

        // 9. 病毒扫描（预留接口，暂未实现）
        if (precheckProperties.getVirusScan().getEnabled()) {
            performVirusScan(bucketName, objectName);
        }

        // 10. 更新提交记录的哈希值
        updateSubmissionHash(submissionId, fileHash);
    }

    /**
     * 1. 验证文件是否存在
     */
    private void validateFileExists(String bucketName, String objectName) {
        boolean exists = minioService.fileExists(bucketName, objectName);
        if (!exists) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.FILE_NOT_FOUND,
                    "文件不存在或路径错误"
            );
        }
        log.debug("文件存在性验证通过");
    }

    /**
     * 获取文件元数据
     */
    private StatObjectResponse getObjectMetadata(String bucketName, String objectName) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.FILE_NOT_FOUND,
                    "无法获取文件元数据: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * 2. 验证文件大小
     */
    private void validateFileSize(long fileSize) {
        long maxSizeBytes = precheckProperties.getMaxFileSize() * 1024 * 1024;
        if (fileSize > maxSizeBytes) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.SIZE_EXCEEDED,
                    String.format("文件大小超出限制（%dMB > %dMB）",
                            fileSize / 1024 / 1024, precheckProperties.getMaxFileSize())
            );
        }
        log.debug("文件大小验证通过: {}MB", fileSize / 1024 / 1024);
    }

    /**
     * 3. 验证文件扩展名
     */
    private void validateFileExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.INVALID_FORMAT,
                    "文件没有扩展名"
            );
        }

        if (!precheckProperties.getAllowedExtensions().contains(extension.toLowerCase())) {
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.INVALID_FORMAT,
                    String.format("不支持的文件格式，仅允许：%s", String.join(", ", precheckProperties.getAllowedExtensions()))
            );
        }
        log.debug("文件扩展名验证通过: {}", extension);
    }

    /**
     * 4. 计算文件哈希
     */
    private String calculateFileHash(InputStream inputStream) {
        String algorithm = precheckProperties.getHashAlgorithm();
        int bufferSize = precheckProperties.getBufferSize();
        return FileHashCalculator.calculateHash(inputStream, algorithm, bufferSize);
    }

    /**
     * 5. 验证压缩包内容
     */
    private void validateArchiveContent(String bucketName, String objectName, long compressedSize) {
        log.info("开始验证压缩包内容");

        try (InputStream inputStream = minioService.downloadFile(bucketName, objectName)) {
            // 只验证ZIP格式（其他格式如RAR、7z需要额外的库）
            String extension = FileTypeDetector.getFileExtension(objectName);
            if ("zip".equalsIgnoreCase(extension)) {
                FileContentValidator.validateZipArchive(
                        inputStream,
                        precheckProperties.getMaxArchiveFiles(),
                        precheckProperties.getMaxArchiveDepth(),
                        precheckProperties.getMaxDecompressedSize() * 1024 * 1024
                );
                log.info("压缩包内容验证通过");
            } else {
                log.info("跳过非ZIP格式压缩包的深度验证: {}", extension);
            }
        } catch (Exception e) {
            if (e instanceof FilePrecheckException) {
                throw (FilePrecheckException) e;
            }
            throw new FilePrecheckException(
                    FilePrecheckException.ErrorCode.CORRUPTED_ARCHIVE,
                    "压缩包验证失败: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * 6. 病毒扫描（预留接口）
     */
    private void performVirusScan(String bucketName, String objectName) {
        log.info("开始病毒扫描: engine={}", precheckProperties.getVirusScan().getEngine());

        // TODO: 集成病毒扫描引擎
        // 可选方案：
        // 1. ClamAV: 开源免费，需要部署daemon服务
        // 2. VirusTotal API: 在线扫描，需要API Key
        // 3. 云服务商提供的内容安全API

        // 示例代码框架：
        // String engine = precheckProperties.getVirusScan().getEngine();
        // switch (engine) {
        //     case "clamav":
        //         scanWithClamAV(bucketName, objectName);
        //         break;
        //     case "virustotal":
        //         scanWithVirusTotal(bucketName, objectName);
        //         break;
        //     default:
        //         log.warn("未知的病毒扫描引擎: {}", engine);
        // }

        log.info("病毒扫描完成（当前为空实现，后续扩展）");
    }

    /**
     * 7. 更新提交记录的哈希值
     */
    private void updateSubmissionHash(Long submissionId, String fileHash) {
        submissionService.updateFileHash(submissionId, fileHash, precheckProperties.getHashAlgorithm());
        log.info("更新提交记录哈希值: submissionId={}, algorithm={}, hash={}",
                submissionId, precheckProperties.getHashAlgorithm(), fileHash);
    }

    /**
     * 判断是否为压缩包文件
     */
    private boolean isArchiveFile(String extension) {
        if (extension == null) {
            return false;
        }
        String ext = extension.toLowerCase();
        return ext.equals("zip") || ext.equals("rar") || ext.equals("7z") ||
                ext.equals("tar") || ext.equals("gz");
    }

    /**
     * 从文件路径提取存储桶名称
     */
    private String extractBucketName(String filePath) {
        // filePath格式: submissions/1/file.zip
        // 默认使用submissions桶
        if (filePath.startsWith("submissions/")) {
            return "submissions";
        } else if (filePath.startsWith("datasets/")) {
            return "datasets";
        }
        return "submissions"; // 默认
    }

    /**
     * 从文件路径提取对象名称
     */
    private String extractObjectName(String filePath) {
        // filePath格式: submissions/1/file.zip
        // 直接返回完整路径作为对象名
        return filePath;
    }
}
