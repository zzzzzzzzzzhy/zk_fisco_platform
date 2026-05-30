package com.wereen.competitionplatform.conig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件预检配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-precheck")
public class FilePrecheckProperties {

    /**
     * 是否启用预检
     */
    private Boolean enabled = true;

    /**
     * 哈希算法 (SHA256/SHA3-256/MD5)
     */
    private String hashAlgorithm = "SHA256";

    /**
     * 最大文件大小（MB）
     */
    private Long maxFileSize = 500L;

    /**
     * 允许的文件扩展名（小写，不带点）
     */
    private Set<String> allowedExtensions = new HashSet<>(Arrays.asList(
            // 文档类
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "txt", "csv",
            // 图片类
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "svg",
            // 压缩包类
            "zip", "rar", "7z", "tar", "gz",
            // 代码脚本类
            "py", "java", "c", "cpp", "cxx", "cc", "h", "hpp", "js", "json",
            "html", "htm", "css", "xml", "sql", "sh", "bash", "sol", "ts",
            "jsx", "tsx", "vue", "php", "rb", "go", "rs", "swift", "kt",
            "scala", "pl", "r", "m", "mat", "pyc"
    ));

    /**
     * 分块读取大小（字节）
     */
    private Integer bufferSize = 8192; // 8KB

    /**
     * 预检超时时间（秒）
     */
    private Integer timeout = 600; // 10分钟

    /**
     * 最大压缩包文件数量
     */
    private Integer maxArchiveFiles = 1000;

    /**
     * 最大压缩包嵌套深度
     */
    private Integer maxArchiveDepth = 5;

    /**
     * 最大解压后大小（MB）
     */
    private Long maxDecompressedSize = 2048L; // 2GB

    /**
     * 临时文件目录
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/precheck";

    /**
     * 病毒扫描配置
     */
    private VirusScanConfig virusScan = new VirusScanConfig();

    @Data
    public static class VirusScanConfig {
        /**
         * 是否启用病毒扫描
         */
        private Boolean enabled = false;

        /**
         * 扫描引擎 (clamav/virustotal/aliyun)
         */
        private String engine = "clamav";

        /**
         * 扫描超时时间（秒）
         */
        private Integer timeout = 300;
    }
}
