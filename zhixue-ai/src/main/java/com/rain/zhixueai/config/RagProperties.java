package com.rain.zhixueai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    
    private FileConfig file = new FileConfig();
    
    private EmbeddingConfig embedding = new EmbeddingConfig();
    
    private RetrievalConfig retrieval = new RetrievalConfig();
    
    private ChunkConfig chunk = new ChunkConfig();
    
    @Data
    public static class FileConfig {
        
        private String uploadPath = "./uploads/rag-documents";
        
        private Long maxSize = 500 * 1024 * 1024L;
        
        private String[] allowedTypes = {"PDF", "DOCX", "TXT", "MD", "DOC", "HTML", "RTF"};
    }
    
    @Data
    public static class EmbeddingConfig {
        
        private Long defaultModelId = 1L;
        
        private Boolean allowModelSwitch = true;
        
        private String ollamaBaseUrl = "http://localhost:11434";
        
        private Integer timeout = 120000;
        
        private Integer parallelism = 2;
        
        private String defaultModel = "nomic-embed-text:latest";
        
        private Integer maxTextLength = 8000;
        
        private Integer dimension;
        
        private Integer batchSize = 5;
        
        private OpenAIConfig openai = new OpenAIConfig();
        
        private ZhipuConfig zhipu = new ZhipuConfig();
        
        private AliyunConfig aliyun = new AliyunConfig();
    }
    
    @Data
    public static class OpenAIConfig {
        
        private String apiKey;
        
        private String endpoint = "https://api.openai.com/v1";
        
        private String defaultModel = "text-embedding-ada-002";
        
        private Integer dimension = 1536;
    }
    
    @Data
    public static class ZhipuConfig {
        
        private String apiKey;
        
        private String endpoint = "https://open.bigmodel.cn/api/paas/v4";
        
        private String defaultModel = "embedding-3";
        
        private Integer dimension = 1024;
    }
    
    @Data
    public static class AliyunConfig {
        
        private String apiKey;
        
        private String endpoint = "https://dashscope.aliyuncs.com/api/v1";
        
        private String defaultModel = "text-embedding-v3";
        
        private Integer dimension = 1024;
    }
    
    @Data
    public static class RetrievalConfig {
        
        private Integer defaultTopK = 8;
        
        private Float defaultThreshold = 0.5f;
        
        private Integer maxContextLength = 6000;
        
        private Float hybridAlpha = 0.6f;
        
        private Boolean rerankEnabled = true;
    }
    
    @Data
    public static class ChunkConfig {
        
        private Integer size = 800;
        
        private Integer overlap = 100;
    }
}
