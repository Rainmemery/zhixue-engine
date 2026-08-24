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
public class RagContext {
    
    private String query;
    
    private List<ContextChunk> chunks;
    
    private String systemPrompt;
    
    private Integer totalTokens;
    
    public boolean hasResults() {
        return chunks != null && !chunks.isEmpty();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextChunk {
        
        private Long chunkId;
        
        private Long documentId;
        
        private String documentTitle;
        
        private String content;
        
        private Float similarity;
    }
}
