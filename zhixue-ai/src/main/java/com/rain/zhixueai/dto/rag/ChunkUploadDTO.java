package com.rain.zhixueai.dto.rag;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadDTO {
    
    private String uploadId;
    
    private String fileName;
    
    private String fileType;
    
    private Long fileSize;
    
    private Integer totalChunks;
    
    private Integer chunkIndex;
    
    private Long chunkSize;
    
    private String chunkHash;
    
    private String knowledgeBaseId;
    
    private String title;
    
    private String userId;
}
