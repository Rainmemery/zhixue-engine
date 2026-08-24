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
public class RetrievalRequest {
    
    private String query;
    
    private List<Long> knowledgeBaseIds;
    
    private Integer topK;
    
    private Float similarityThreshold;
    
    private Boolean includeContent;
}
