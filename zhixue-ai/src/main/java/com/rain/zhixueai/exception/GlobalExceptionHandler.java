package com.rain.zhixueai.exception;

import com.rain.zhixuecommon.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(FileUploadException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleFileUploadException(FileUploadException e) {
        log.error("文件上传异常: {} - {}", e.getErrorCode(), e.getMessage());
        return Result.wrong(e.getMessage());
    }
    
    @ExceptionHandler(DocumentProcessingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleDocumentProcessingException(DocumentProcessingException e) {
        log.error("文档处理异常: documentId={}, message={}", e.getDocumentId(), e.getMessage(), e);
        return Result.wrong(e.getMessage());
    }
    
    @ExceptionHandler(RagException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleRagException(RagException e) {
        log.error("RAG异常: {} - moduleCode={}", e.getMessage(), e.getModuleCode(), e);
        return Result.wrong(e.getMessage());
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件大小超过限制: {}", e.getMessage());
        return Result.wrong("文件大小超过服务器限制，请使用分片上传功能");
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return Result.wrong(e.getMessage());
    }
    
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleIllegalStateException(IllegalStateException e) {
        log.error("状态错误: {}", e.getMessage(), e);
        return Result.wrong(e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleGenericException(Exception e) {
        log.error("未处理的异常: {}", e.getMessage(), e);
        return Result.wrong("服务器内部错误: " + e.getMessage());
    }
}
