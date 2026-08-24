package com.rain.zhixueai.service.rag.impl;

import com.rain.zhixueai.dto.rag.EmbeddingModelDTO;
import com.rain.zhixueai.entity.rag.RagEmbeddingModel;
import com.rain.zhixueai.mapper.rag.RagEmbeddingModelMapper;
import com.rain.zhixueai.service.rag.EmbeddingModelService;
import com.rain.zhixueai.service.rag.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmbeddingModelServiceImpl implements EmbeddingModelService {
    
    @Autowired
    private RagEmbeddingModelMapper embeddingModelMapper;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Override
    public List<EmbeddingModelDTO> listAll() {
        return embeddingModelMapper.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<EmbeddingModelDTO> listEnabled() {
        return embeddingModelMapper.findAllEnabled().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public EmbeddingModelDTO getById(Long id) {
        RagEmbeddingModel model = embeddingModelMapper.findById(id);
        return model != null ? toDTO(model) : null;
    }
    
    @Override
    public EmbeddingModelDTO getByModelName(String modelName) {
        RagEmbeddingModel model = embeddingModelMapper.findByModelName(modelName);
        return model != null ? toDTO(model) : null;
    }
    
    @Override
    public EmbeddingModelDTO getDefaultModel() {
        RagEmbeddingModel model = embeddingModelMapper.findDefault();
        return model != null ? toDTO(model) : null;
    }
    
    @Override
    @Transactional
    public EmbeddingModelDTO create(EmbeddingModelDTO dto) {
        RagEmbeddingModel existing = embeddingModelMapper.findByModelName(dto.getModelName());
        if (existing != null) {
            throw new IllegalArgumentException("模型名称已存在: " + dto.getModelName());
        }
        
        RagEmbeddingModel model = new RagEmbeddingModel();
        BeanUtils.copyProperties(dto, model);
        
        if (model.getIsDefault() == null) {
            model.setIsDefault(false);
        }
        if (model.getStatus() == null) {
            model.setStatus(true);
        }
        
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            embeddingModelMapper.clearDefault();
        }
        
        embeddingModelMapper.insert(model);
        log.info("Created embedding model: {}", model.getModelName());
        
        return toDTO(model);
    }
    
    @Override
    @Transactional
    public EmbeddingModelDTO update(EmbeddingModelDTO dto) {
        RagEmbeddingModel existing = embeddingModelMapper.findById(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("模型不存在: " + dto.getId());
        }
        
        if (!existing.getModelName().equals(dto.getModelName())) {
            RagEmbeddingModel nameConflict = embeddingModelMapper.findByModelName(dto.getModelName());
            if (nameConflict != null) {
                throw new IllegalArgumentException("模型名称已存在: " + dto.getModelName());
            }
        }
        
        RagEmbeddingModel model = new RagEmbeddingModel();
        BeanUtils.copyProperties(dto, model);
        
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            embeddingModelMapper.clearDefault();
        }
        
        embeddingModelMapper.update(model);
        log.info("Updated embedding model: {}", model.getModelName());
        
        return toDTO(embeddingModelMapper.findById(dto.getId()));
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        RagEmbeddingModel model = embeddingModelMapper.findById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + id);
        }
        
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            throw new IllegalStateException("无法删除默认模型，请先设置其他模型为默认");
        }
        
        embeddingModelMapper.delete(id);
        log.info("Deleted embedding model: {}", model.getModelName());
    }
    
    @Override
    @Transactional
    public void setDefault(Long id) {
        RagEmbeddingModel model = embeddingModelMapper.findById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + id);
        }
        
        if (!Boolean.TRUE.equals(model.getStatus())) {
            throw new IllegalStateException("无法将禁用的模型设为默认");
        }
        
        embeddingModelMapper.clearDefault();
        embeddingModelMapper.setDefault(id);
        log.info("Set default embedding model: {}", model.getModelName());
    }
    
    @Override
    @Transactional
    public void setStatus(Long id, boolean enabled) {
        RagEmbeddingModel model = embeddingModelMapper.findById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + id);
        }
        
        if (!enabled && Boolean.TRUE.equals(model.getIsDefault())) {
            throw new IllegalStateException("无法禁用默认模型，请先设置其他模型为默认");
        }
        
        embeddingModelMapper.updateStatus(id, enabled);
        log.info("Set embedding model status: {} -> {}", model.getModelName(), enabled);
    }
    
    @Override
    public Map<String, Object> validateModel(String modelName) {
        Map<String, Object> result = new HashMap<>();
        result.put("modelName", modelName);
        
        boolean available = embeddingService.isModelAvailable(modelName);
        result.put("available", available);
        
        if (available) {
            try {
                int dimension = embeddingService.getDimension(modelName);
                result.put("dimension", dimension);
                result.put("message", "模型可用");
            } catch (Exception e) {
                result.put("available", false);
                result.put("message", "模型可用但无法获取维度: " + e.getMessage());
            }
        } else {
            result.put("message", "模型不可用，请确保已在Ollama中拉取该模型");
        }
        
        return result;
    }
    
    private EmbeddingModelDTO toDTO(RagEmbeddingModel model) {
        EmbeddingModelDTO dto = new EmbeddingModelDTO();
        BeanUtils.copyProperties(model, dto);
        return dto;
    }
}
