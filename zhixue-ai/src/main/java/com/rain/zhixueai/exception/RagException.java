package com.rain.zhixueai.exception;

public class RagException extends RuntimeException {
    
    private final String errorCode;
    private final String moduleCode;
    
    public RagException(String message) {
        super(message);
        this.errorCode = "RAG_ERROR";
        this.moduleCode = null;
    }
    
    public RagException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.moduleCode = null;
    }
    
    public RagException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "RAG_ERROR";
        this.moduleCode = null;
    }
    
    public RagException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.moduleCode = null;
    }
    
    public RagException(String message, String errorCode, String moduleCode) {
        super(message);
        this.errorCode = errorCode;
        this.moduleCode = moduleCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getModuleCode() {
        return moduleCode;
    }
    
    public static RagException knowledgeBaseNotFound(Long id) {
        return new RagException(
                String.format("知识库不存在: %d", id),
                "KNOWLEDGE_BASE_NOT_FOUND"
        );
    }
    
    public static RagException embeddingFailed(String modelName, Throwable cause) {
        return new RagException(
                String.format("向量化失败: model=%s", modelName),
                "EMBEDDING_FAILED",
                cause
        );
    }
    
    public static RagException retrievalFailed(String query, Throwable cause) {
        return new RagException(
                String.format("检索失败: query=%s", query.substring(0, Math.min(50, query.length()))),
                "RETRIEVAL_FAILED",
                cause
        );
    }
    
    public static RagException moduleNotConfigured(String moduleCode) {
        return new RagException(
                String.format("模块未配置知识库: %s", moduleCode),
                "MODULE_NOT_CONFIGURED",
                moduleCode
        );
    }
}
