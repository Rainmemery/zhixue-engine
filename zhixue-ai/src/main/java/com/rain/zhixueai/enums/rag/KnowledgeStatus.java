package com.rain.zhixueai.enums.rag;

public enum KnowledgeStatus {
    
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");
    
    private final int code;
    private final String description;
    
    KnowledgeStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static KnowledgeStatus fromCode(int code) {
        for (KnowledgeStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown knowledge status code: " + code);
    }
}
