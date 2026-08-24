package com.rain.zhixueai.service.rag;

import com.rain.zhixueai.dto.rag.EmbeddingModelDTO;

import java.util.List;
import java.util.Map;

public interface EmbeddingModelService {
    
    List<EmbeddingModelDTO> listAll();
    
    List<EmbeddingModelDTO> listEnabled();
    
    EmbeddingModelDTO getById(Long id);
    
    EmbeddingModelDTO getByModelName(String modelName);
    
    EmbeddingModelDTO getDefaultModel();
    
    EmbeddingModelDTO create(EmbeddingModelDTO dto);
    
    EmbeddingModelDTO update(EmbeddingModelDTO dto);
    
    void delete(Long id);
    
    void setDefault(Long id);
    
    void setStatus(Long id, boolean enabled);
    
    Map<String, Object> validateModel(String modelName);
}
