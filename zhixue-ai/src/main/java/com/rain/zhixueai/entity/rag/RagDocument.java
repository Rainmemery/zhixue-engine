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
public class RagDocument {
    
    private Long id;
    
    private Long knowledgeBaseId;
    
    private String title;
    
    private String fileName;
    
    private String fileType;
    
    private Long fileSize;
    
    private String filePath;
    
    private Integer chunkCount;
    
    private Integer status;
    
    private String errorMessage;
    
    private Long createdBy;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
