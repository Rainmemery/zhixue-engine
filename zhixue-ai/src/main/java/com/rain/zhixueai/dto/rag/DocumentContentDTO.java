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
public class DocumentContentDTO {
    
    private Long id;
    
    private String title;
    
    private String fileName;
    
    private String fileType;
    
    private Long fileSize;
    
    private String fullContent;
    
    private List<ContentPage> pages;
    
    private int totalPages;
    
    private int currentPage;
    
    private int pageSize;
    
    private String searchKeyword;
    
    private List<SearchMatch> searchMatches;
    
    private int totalMatches;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContentPage {
        private int pageNumber;
        private String content;
        private int startOffset;
        private int endOffset;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchMatch {
        private int pageNumber;
        private int startOffset;
        private int endOffset;
        private String context;
    }
}
