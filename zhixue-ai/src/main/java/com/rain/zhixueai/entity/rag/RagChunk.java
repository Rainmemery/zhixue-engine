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
public class RagChunk {
    
    private Long id;
    
    private Long documentId;
    
    private Long knowledgeBaseId;
    
    private Integer chunkIndex;
    
    private String content;
    
    private String vectorId;
    
    private LocalDateTime createdAt;
}
