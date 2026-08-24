package com.rain.zhixueai.entity.rag;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagChunkUpload {
    
    private Long id;
    
    private String uploadId;
    
    private String fileName;
    
    private String fileType;
    
    private Long fileSize;
    
    private Integer totalChunks;
    
    private String tempPath;
    
    private Long knowledgeBaseId;
    
    private String title;
    
    private Long createdBy;
    
    private Integer status;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_UPLOADING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = 3;
}
