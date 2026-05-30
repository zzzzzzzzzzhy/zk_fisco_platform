package com.wereen.competitionplatform.exception;

/**
 * 文件预检异常
 */
public class FilePrecheckException extends RuntimeException {

    private String errorCode;

    public FilePrecheckException(String message) {
        super(message);
    }

    public FilePrecheckException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FilePrecheckException(String message, Throwable cause) {
        super(message, cause);
    }

    public FilePrecheckException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 预定义错误码
     */
    public static class ErrorCode {
        public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
        public static final String INVALID_FORMAT = "INVALID_FORMAT";
        public static final String SIZE_EXCEEDED = "SIZE_EXCEEDED";
        public static final String CORRUPTED_ARCHIVE = "CORRUPTED_ARCHIVE";
        public static final String MALICIOUS_CONTENT = "MALICIOUS_CONTENT";
        public static final String VIRUS_DETECTED = "VIRUS_DETECTED";
        public static final String HASH_FAILED = "HASH_FAILED";
        public static final String TIMEOUT = "TIMEOUT";
    }
}
