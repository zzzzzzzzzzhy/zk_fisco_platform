package com.wereen.competitionplatform.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件类型检测工具类 - 基于魔数（Magic Number）验证
 */
@Slf4j
public class FileTypeDetector {

    /**
     * 魔数映射表：文件头字节 -> 文件扩展名
     */
    private static final Map<String, String> MAGIC_NUMBER_MAP = new HashMap<>();

    static {
        // 压缩包类
        MAGIC_NUMBER_MAP.put("504B0304", "zip");       // ZIP
        MAGIC_NUMBER_MAP.put("504B0506", "zip");       // ZIP (empty)
        MAGIC_NUMBER_MAP.put("504B0708", "zip");       // ZIP (spanned)
        MAGIC_NUMBER_MAP.put("52617221", "rar");       // RAR v1.5+
        MAGIC_NUMBER_MAP.put("526172211A07", "rar");   // RAR v5.0+
        MAGIC_NUMBER_MAP.put("377ABCAF271C", "7z");    // 7-Zip
        MAGIC_NUMBER_MAP.put("1F8B", "gz");            // GZIP
        MAGIC_NUMBER_MAP.put("1F9D", "tar");           // TAR (compressed)
        MAGIC_NUMBER_MAP.put("7573746172", "tar");     // TAR (ustar)

        // 图片类
        MAGIC_NUMBER_MAP.put("FFD8FF", "jpg");         // JPEG/JPG
        MAGIC_NUMBER_MAP.put("89504E47", "png");       // PNG
        MAGIC_NUMBER_MAP.put("474946383761", "gif");   // GIF87a
        MAGIC_NUMBER_MAP.put("474946383961", "gif");   // GIF89a
        MAGIC_NUMBER_MAP.put("424D", "bmp");           // BMP
        MAGIC_NUMBER_MAP.put("49492A00", "tiff");      // TIFF (little-endian)
        MAGIC_NUMBER_MAP.put("4D4D002A", "tiff");      // TIFF (big-endian)
        MAGIC_NUMBER_MAP.put("52494646", "webp");      // WebP (需结合WEBP标识)
        MAGIC_NUMBER_MAP.put("3C737667", "svg");       // SVG (<svg)
        MAGIC_NUMBER_MAP.put("3C3F786D6C", "svg");     // SVG (<?xml)

        // 文档类
        MAGIC_NUMBER_MAP.put("25504446", "pdf");       // PDF
        MAGIC_NUMBER_MAP.put("D0CF11E0A1B11AE1", "doc"); // MS Office (DOC/XLS/PPT)
        MAGIC_NUMBER_MAP.put("504B030414000600", "docx"); // Office 2007+ (DOCX/XLSX/PPTX)
        MAGIC_NUMBER_MAP.put("7B5C72746631", "rtf");   // RTF

        // 代码类（部分文本文件无魔数，需要其他方式验证）
        MAGIC_NUMBER_MAP.put("23212F", "sh");          // Shell Script (#!/)
        MAGIC_NUMBER_MAP.put("7F454C46", "elf");       // ELF executable (Linux)
        MAGIC_NUMBER_MAP.put("4D5A", "exe");           // Windows EXE
        MAGIC_NUMBER_MAP.put("CAFEBABE", "class");     // Java Class
    }

    /**
     * 检测文件类型
     *
     * @param inputStream 文件输入流
     * @return 检测到的文件扩展名，如果无法识别返回null
     */
    public static String detectFileType(InputStream inputStream) throws IOException {
        byte[] headerBytes = new byte[16]; // 读取前16字节
        int bytesRead = inputStream.read(headerBytes);

        if (bytesRead <= 0) {
            log.warn("文件为空或无法读取");
            return null;
        }

        String magicNumber = bytesToHex(headerBytes, bytesRead);

        // 尝试匹配完整魔数
        for (Map.Entry<String, String> entry : MAGIC_NUMBER_MAP.entrySet()) {
            if (magicNumber.startsWith(entry.getKey())) {
                log.debug("检测到文件类型: {} (魔数: {})", entry.getValue(), entry.getKey());
                return entry.getValue();
            }
        }

        // 特殊处理：WebP需要检查RIFF容器
        if (magicNumber.startsWith("52494646") && bytesRead >= 12) {
            byte[] webpCheck = new byte[4];
            inputStream.mark(16);
            inputStream.skip(8); // 跳过RIFF和大小
            inputStream.read(webpCheck);
            inputStream.reset();
            if ("WEBP".equals(new String(webpCheck))) {
                return "webp";
            }
        }

        log.debug("无法通过魔数识别文件类型，魔数: {}", magicNumber);
        return null;
    }

    /**
     * 验证文件扩展名与魔数是否匹配
     *
     * @param inputStream 文件输入流
     * @param extension   文件扩展名（不带点）
     * @return 是否匹配
     */
    public static boolean validateFileType(InputStream inputStream, String extension) {
        try {
            inputStream.mark(16); // 标记位置以便重置
            String detectedType = detectFileType(inputStream);
            inputStream.reset(); // 重置流位置

            if (detectedType == null) {
                // 对于纯文本文件（如txt, csv, json, py等），无魔数，仅通过扩展名验证
                return isTextBasedExtension(extension);
            }

            // 处理扩展名别名
            String normalizedExtension = normalizeExtension(extension);
            String normalizedDetectedType = normalizeExtension(detectedType);

            return normalizedDetectedType.equals(normalizedExtension);

        } catch (IOException e) {
            log.error("验证文件类型失败", e);
            return false;
        }
    }

    /**
     * 判断是否为文本类文件扩展名（无魔数）
     */
    private static boolean isTextBasedExtension(String extension) {
        String[] textExtensions = {
                "txt", "csv", "json", "xml", "html", "htm", "css", "js", "ts", "jsx", "tsx",
                "py", "java", "c", "cpp", "cxx", "cc", "h", "hpp", "sh", "bash", "sql",
                "php", "rb", "go", "rs", "swift", "kt", "scala", "pl", "r", "m", "mat",
                "vue", "sol", "md", "yml", "yaml", "toml", "ini", "conf"
        };
        for (String ext : textExtensions) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标准化扩展名（处理别名）
     */
    private static String normalizeExtension(String extension) {
        if (extension == null) {
            return null;
        }
        extension = extension.toLowerCase();

        // 处理别名
        switch (extension) {
            case "jpeg":
                return "jpg";
            case "tif":
                return "tiff";
            case "htm":
                return "html";
            case "cxx":
            case "cc":
                return "cpp";
            case "hpp":
                return "h";
            default:
                return extension;
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }

    /**
     * 从文件名提取扩展名
     *
     * @param fileName 文件名
     * @return 扩展名（小写，不带点），如果没有扩展名返回null
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return null;
        }

        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
}
