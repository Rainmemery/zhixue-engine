package com.rain.zhixueai.service.rag;

import com.rain.zhixueai.dto.rag.KnowledgeDTO;

import java.util.List;

public interface KnowledgeService {
    
    List<KnowledgeDTO> list(String keyword, int page, int size);
    
    List<KnowledgeDTO> listEnabled();
    
    KnowledgeDTO getById(Long id);
    
    KnowledgeDTO create(KnowledgeDTO dto);
    
    KnowledgeDTO update(KnowledgeDTO dto);
    
    void delete(Long id);
    
    void setStatus(Long id, boolean enabled);
    
    void incrementDocumentCount(Long id);
    
    void decrementDocumentCount(Long id);
    
    void updateChunkCount(Long id, int delta);
}
