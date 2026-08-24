package com.rain.zhixueai.exception;

public class FileUploadException extends RuntimeException {
    
    private final String errorCode;
    private final String fileName;
    private final Long fileSize;
    
    public FileUploadException(String message) {
        super(message);
        this.errorCode = "FILE_UPLOAD_ERROR";
        this.fileName = null;
        this.fileSize = null;
    }
    
    public FileUploadException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.fileName = null;
        this.fileSize = null;
    }
    
    public FileUploadException(String message, String fileName, Long fileSize) {
        super(message);
        this.errorCode = "FILE_UPLOAD_ERROR";
        this.fileName = fileName;
        this.fileSize = fileSize;
    }
    
    public FileUploadException(String message, String errorCode, String fileName, Long fileSize) {
        super(message);
        this.errorCode = errorCode;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }
    
    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "FILE_UPLOAD_ERROR";
        this.fileName = null;
        this.fileSize = null;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public static FileUploadException fileTooLarge(String fileName, Long fileSize, Long maxSize) {
        return new FileUploadException(
                String.format("文件大小超过限制: %s (%.2fMB > %.2fMB)", 
                        fileName, fileSize / 1024.0 / 1024.0, maxSize / 1024.0 / 1024.0),
                "FILE_TOO_LARGE",
                fileName,
                fileSize
        );
    }
    
    public static FileUploadException unsupportedType(String fileName, String fileType) {
        return new FileUploadException(
                String.format("不支持的文件类型: %s (%s)", fileName, fileType),
                "UNSUPPORTED_FILE_TYPE",
                fileName,
                null
        );
    }
    
    public static FileUploadException chunkUploadFailed(String uploadId, int chunkIndex, String reason) {
        return new FileUploadException(
                String.format("分片上传失败: uploadId=%s, chunk=%d, reason=%s", uploadId, chunkIndex, reason),
                "CHUNK_UPLOAD_FAILED"
        );
    }
}
