package com.rain.zhixueai.service.rag;

import com.rain.zhixueai.dto.rag.RagContext;
import com.rain.zhixueai.dto.rag.RetrievalRequest;
import com.rain.zhixueai.dto.rag.RetrievalResult;
import com.rain.zhixueai.entity.rag.RagChunk;

import java.util.List;

public interface RetrievalService {
    
    RetrievalResult search(RetrievalRequest request);
    
    List<RagChunk> retrieve(String query, List<Long> knowledgeBaseIds, int topK, float threshold);
    
    RagContext buildContext(String query, List<Long> knowledgeBaseIds);
    
    void storeEmbeddings(Long knowledgeBaseId, Long documentId, List<String> contents, List<float[]> embeddings);
    
    void deleteEmbeddingsByDocument(Long documentId);
    
    void deleteEmbeddingsByKnowledgeBase(Long knowledgeBaseId);
}
