package com.rain.zhixueai.service.rag;

import java.util.List;

public interface EmbeddingService {
    
    float[] embed(String text);
    
    float[] embed(String text, String modelName);
    
    List<float[]> batchEmbed(List<String> texts);
    
    List<float[]> batchEmbed(List<String> texts, String modelName);
    
    int getDimension();
    
    int getDimension(String modelName);
    
    boolean isModelAvailable(String modelName);
}
