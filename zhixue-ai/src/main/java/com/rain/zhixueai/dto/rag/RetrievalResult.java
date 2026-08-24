package com.rain.zhixueai.dto.rag;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievalResult {
    
    private String query;
    
    private List<RetrievalItem> items;
    
    private Integer totalResults;
    
    private Long searchTimeMs;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetrievalItem {
        
        private Long chunkId;
        
        private Long documentId;
        
        private Long knowledgeBaseId;
        
        private String documentTitle;
        
        private String knowledgeBaseName;
        
        private String content;
        
        private Float similarity;
        
        private Integer chunkIndex;
    }
}
