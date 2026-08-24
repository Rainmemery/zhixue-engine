package com.rain.zhixueai.dto.rag;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeDTO {
    
    private Long id;
    
    private String name;
    
    private String description;
    
    private Long embeddingModelId;
    
    private String embeddingModelName;
    
    private Integer embeddingDimension;
    
    private String milvusCollection;
    
    private Integer documentCount;
    
    private Integer chunkCount;
    
    private Boolean status;
    
    private Long ownerId;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
