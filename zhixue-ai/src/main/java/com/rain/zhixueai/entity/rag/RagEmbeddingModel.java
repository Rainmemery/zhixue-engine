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
public class RagEmbeddingModel {
    
    private Long id;
    
    private String modelName;
    
    private String displayName;
    
    private Integer dimension;
    
    private String description;
    
    private Boolean isDefault;
    
    private Boolean status;
    
    private String modelType;
    
    private String apiEndpoint;
    
    private String apiKey;
    
    private String provider;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
