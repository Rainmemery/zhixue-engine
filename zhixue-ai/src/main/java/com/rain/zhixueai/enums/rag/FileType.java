package com.rain.zhixueai.enums.rag;

public enum FileType {
    
    PDF("PDF", "PDF文档"),
    DOCX("DOCX", "Word文档"),
    TXT("TXT", "纯文本"),
    MD("MD", "Markdown文档");
    
    private final String code;
    private final String description;
    
    FileType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static FileType fromCode(String code) {
        for (FileType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown file type: " + code);
    }
    
    public static FileType fromExtension(String extension) {
        String ext = extension.toLowerCase().replace(".", "");
        switch (ext) {
            case "pdf":
                return PDF;
            case "docx":
            case "doc":
                return DOCX;
            case "txt":
                return TXT;
            case "md":
            case "markdown":
                return MD;
            default:
                throw new IllegalArgumentException("Unsupported file extension: " + extension);
        }
    }
}
