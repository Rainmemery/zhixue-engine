package com.rain.zhixueai.exception;

public class DocumentProcessingException extends RuntimeException {
    
    private final String errorCode;
    private final Long documentId;
    
    public DocumentProcessingException(String message) {
        super(message);
        this.errorCode = "DOCUMENT_PROCESSING_ERROR";
        this.documentId = null;
    }
    
    public DocumentProcessingException(String message, Long documentId) {
        super(message);
        this.errorCode = "DOCUMENT_PROCESSING_ERROR";
        this.documentId = documentId;
    }
    
    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DOCUMENT_PROCESSING_ERROR";
        this.documentId = null;
    }
    
    public DocumentProcessingException(String message, Long documentId, Throwable cause) {
        super(message, cause);
        this.errorCode = "DOCUMENT_PROCESSING_ERROR";
        this.documentId = documentId;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Long getDocumentId() {
        return documentId;
    }
}
