package com.rain.zhixueai.enums.rag;

public enum DocumentStatus {
    
    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");
    
    private final int code;
    private final String description;
    
    DocumentStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static DocumentStatus fromCode(int code) {
        for (DocumentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown document status code: " + code);
    }
}
