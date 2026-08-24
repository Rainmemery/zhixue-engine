package com.rain.zhixueai.service.rag.impl;

import com.rain.zhixueai.config.MilvusClientProvider;
import com.rain.zhixueai.config.MilvusProperties;
import com.rain.zhixueai.dto.rag.KnowledgeDTO;
import com.rain.zhixueai.entity.rag.RagEmbeddingModel;
import com.rain.zhixueai.entity.rag.RagKnowledgeBase;
import com.rain.zhixueai.mapper.rag.RagDocumentMapper;
import com.rain.zhixueai.mapper.rag.RagEmbeddingModelMapper;
import com.rain.zhixueai.mapper.rag.RagKnowledgeBaseMapper;
import com.rain.zhixueai.service.rag.KnowledgeService;
import com.rain.zhixueai.service.rag.RetrievalService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {
    
    @Autowired
    private RagKnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private RagEmbeddingModelMapper embeddingModelMapper;
    
    @Autowired
    private RagDocumentMapper documentMapper;
    
    @Autowired
    private MilvusClientProvider milvusClientProvider;
    
    @Autowired
    private MilvusProperties milvusProperties;
    
    private MilvusClientV2 getMilvusClient() {
        return milvusClientProvider.getClient();
    }
    
    private boolean isMilvusAvailable() {
        if (!milvusClientProvider.isAvailable()) {
            log.warn("Milvus客户端未初始化，知识库向量操作不可用");
            return false;
        }
        return true;
    }
    
    @Autowired
    private RetrievalService retrievalService;
    
    @Override
    public List<KnowledgeDTO> list(String keyword, int page, int size) {
        List<RagKnowledgeBase> knowledgeBases;
        if (keyword != null && !keyword.isEmpty()) {
            knowledgeBases = knowledgeBaseMapper.findByKeyword(keyword);
        } else {
            knowledgeBases = knowledgeBaseMapper.findAll();
        }
        
        int start = (page - 1) * size;
        int end = Math.min(start + size, knowledgeBases.size());
        
        if (start >= knowledgeBases.size()) {
            return new ArrayList<>();
        }
        
        return knowledgeBases.subList(start, end).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<KnowledgeDTO> listEnabled() {
        return knowledgeBaseMapper.findEnabled().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public KnowledgeDTO getById(Long id) {
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(id);
        return kb != null ? toDTO(kb) : null;
    }
    
    @Override
    @Transactional
    public KnowledgeDTO create(KnowledgeDTO dto) {
        RagEmbeddingModel embeddingModel = null;
        if (dto.getEmbeddingModelId() != null) {
            embeddingModel = embeddingModelMapper.findById(dto.getEmbeddingModelId());
            if (embeddingModel == null) {
                throw new IllegalArgumentException("Embedding模型不存在: " + dto.getEmbeddingModelId());
            }
        } else {
            embeddingModel = embeddingModelMapper.findDefault();
            if (embeddingModel == null) {
                throw new IllegalStateException("未找到默认Embedding模型");
            }
            dto.setEmbeddingModelId(embeddingModel.getId());
        }
        
        String collectionName = getCollectionName(embeddingModel.getDimension());
        dto.setMilvusCollection(collectionName);
        dto.setEmbeddingDimension(embeddingModel.getDimension());
        dto.setEmbeddingModelName(embeddingModel.getModelName());
        
        createCollectionIfNotExists(collectionName, embeddingModel.getDimension());
        
        RagKnowledgeBase kb = new RagKnowledgeBase();
        BeanUtils.copyProperties(dto, kb);
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        kb.setStatus(true);
        
        knowledgeBaseMapper.insert(kb);
        log.info("Created knowledge base: {} with embedding model: {}", kb.getName(), embeddingModel.getModelName());
        
        return toDTO(kb);
    }
    
    @Override
    @Transactional
    public KnowledgeDTO update(KnowledgeDTO dto) {
        RagKnowledgeBase existing = knowledgeBaseMapper.findById(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("知识库不存在: " + dto.getId());
        }
        
        if (!existing.getEmbeddingModelId().equals(dto.getEmbeddingModelId())) {
            throw new IllegalArgumentException("知识库创建后不可更换Embedding模型");
        }
        
        RagKnowledgeBase kb = new RagKnowledgeBase();
        BeanUtils.copyProperties(dto, kb);
        kb.setEmbeddingModelId(existing.getEmbeddingModelId());
        kb.setMilvusCollection(existing.getMilvusCollection());
        
        knowledgeBaseMapper.update(kb);
        log.info("Updated knowledge base: {}", kb.getName());
        
        return toDTO(knowledgeBaseMapper.findById(dto.getId()));
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(id);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + id);
        }
        
        try {
            retrievalService.deleteEmbeddingsByKnowledgeBase(id);
            log.info("Deleted embeddings for knowledge base: {}", kb.getName());
        } catch (Exception e) {
            log.warn("Failed to delete embeddings for knowledge base {}: {}", kb.getName(), e.getMessage());
        }
        
        int documentCount = documentMapper.deleteByKnowledgeBaseId(id);
        log.info("Deleted {} documents for knowledge base: {}", documentCount, kb.getName());
        
        knowledgeBaseMapper.delete(id);
        log.info("Deleted knowledge base: {}", kb.getName());
    }
    
    public Map<String, Object> getStatistics(Long id) {
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(id);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + id);
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("id", kb.getId());
        stats.put("name", kb.getName());
        stats.put("documentCount", kb.getDocumentCount());
        stats.put("chunkCount", kb.getChunkCount());
        stats.put("status", kb.getStatus());
        stats.put("createdAt", kb.getCreatedAt());
        stats.put("updatedAt", kb.getUpdatedAt());
        
        if (kb.getEmbeddingModelId() != null) {
            RagEmbeddingModel model = embeddingModelMapper.findById(kb.getEmbeddingModelId());
            if (model != null) {
                stats.put("embeddingModel", model.getModelName());
                stats.put("embeddingDimension", model.getDimension());
            }
        }
        
        return stats;
    }
    
    public List<Map<String, Object>> getAllStatistics() {
        List<RagKnowledgeBase> knowledgeBases = knowledgeBaseMapper.findAll();
        return knowledgeBases.stream()
                .map(kb -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("id", kb.getId());
                    stats.put("name", kb.getName());
                    stats.put("documentCount", kb.getDocumentCount());
                    stats.put("chunkCount", kb.getChunkCount());
                    stats.put("status", kb.getStatus());
                    return stats;
                })
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void reindexAll(Long id) {
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(id);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + id);
        }
        
        log.info("Starting reindex for knowledge base: {}", kb.getName());
        
        retrievalService.deleteEmbeddingsByKnowledgeBase(id);
        
        if (kb.getChunkCount() != null && kb.getChunkCount() > 0) {
            knowledgeBaseMapper.decrementChunkCount(id, kb.getChunkCount());
        }
        
        log.info("Reindex initiated for knowledge base: {}", kb.getName());
    }
    
    @Override
    @Transactional
    public void setStatus(Long id, boolean enabled) {
        knowledgeBaseMapper.updateStatus(id, enabled);
        log.info("Set knowledge base status: {} -> {}", id, enabled);
    }
    
    @Override
    @Transactional
    public void incrementDocumentCount(Long id) {
        knowledgeBaseMapper.incrementDocumentCount(id);
    }
    
    @Override
    @Transactional
    public void decrementDocumentCount(Long id) {
        knowledgeBaseMapper.decrementDocumentCount(id);
    }
    
    @Override
    @Transactional
    public void updateChunkCount(Long id, int delta) {
        if (delta > 0) {
            knowledgeBaseMapper.incrementChunkCount(id, delta);
        } else if (delta < 0) {
            knowledgeBaseMapper.decrementChunkCount(id, -delta);
        }
    }
    
    private String getCollectionName(int dimension) {
        return milvusProperties.getCollectionPrefix() + "_" + dimension;
    }
    
    private void createCollectionIfNotExists(String collectionName, int dimension) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus不可用，无法创建集合: {}", collectionName);
            return;
        }
        
        try {
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            
            boolean hasCollection = getMilvusClient().hasCollection(hasCollectionReq);
            
            if (hasCollection) {
                if (!isCollectionSchemaValid(collectionName)) {
                    log.warn("Collection {} has invalid schema (id field is not VarChar), dropping and recreating...", collectionName);
                    dropCollection(collectionName);
                    hasCollection = false;
                }
            }
            
            if (!hasCollection) {
                log.info("Creating Milvus collection: {} with dimension: {}", collectionName, dimension);
                
                CreateCollectionReq.FieldSchema idField = CreateCollectionReq.FieldSchema.builder()
                        .name("id")
                        .dataType(DataType.VarChar)
                        .maxLength(64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build();
                
                CreateCollectionReq.FieldSchema knowledgeBaseIdField = CreateCollectionReq.FieldSchema.builder()
                        .name("knowledge_base_id")
                        .dataType(DataType.Int64)
                        .build();
                
                CreateCollectionReq.FieldSchema documentIdField = CreateCollectionReq.FieldSchema.builder()
                        .name("document_id")
                        .dataType(DataType.Int64)
                        .build();
                
                CreateCollectionReq.FieldSchema chunkIdField = CreateCollectionReq.FieldSchema.builder()
                        .name("chunk_id")
                        .dataType(DataType.Int64)
                        .build();
                
                CreateCollectionReq.FieldSchema contentField = CreateCollectionReq.FieldSchema.builder()
                        .name("content")
                        .dataType(DataType.VarChar)
                        .maxLength(8192)
                        .build();
                
                CreateCollectionReq.FieldSchema vectorField = CreateCollectionReq.FieldSchema.builder()
                        .name("vector")
                        .dataType(DataType.FloatVector)
                        .dimension(dimension)
                        .build();
                
                CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema.builder()
                        .fieldSchemaList(Arrays.asList(
                                idField, 
                                knowledgeBaseIdField, 
                                documentIdField, 
                                chunkIdField, 
                                contentField, 
                                vectorField))
                        .build();
                
                CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                        .collectionName(collectionName)
                        .collectionSchema(collectionSchema)
                        .build();
                
                getMilvusClient().createCollection(createCollectionReq);
                
                log.info("Created Milvus collection: {} with custom schema", collectionName);
            }
        } catch (Exception e) {
            log.error("Failed to create Milvus collection: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Milvus collection: " + e.getMessage(), e);
        }
    }
    
    private boolean isCollectionSchemaValid(String collectionName) {
        if (!isMilvusAvailable()) {
            return false;
        }
        
        try {
            DescribeCollectionReq describeReq = DescribeCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            
            DescribeCollectionResp describeResp = getMilvusClient().describeCollection(describeReq);
            
            CreateCollectionReq.CollectionSchema schema = describeResp.getCollectionSchema();
            if (schema == null) {
                return false;
            }
            
            List<CreateCollectionReq.FieldSchema> fields = schema.getFieldSchemaList();
            if (fields == null || fields.isEmpty()) {
                return false;
            }
            
            for (CreateCollectionReq.FieldSchema field : fields) {
                if ("id".equals(field.getName())) {
                    DataType idType = field.getDataType();
                    log.debug("Collection {} id field type: {}", collectionName, idType);
                    return idType == DataType.VarChar;
                }
            }
            
            return false;
        } catch (Exception e) {
            log.warn("Failed to describe collection {}: {}", collectionName, e.getMessage());
            return false;
        }
    }
    
    private void dropCollection(String collectionName) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus不可用，无法删除集合: {}", collectionName);
            return;
        }
        
        try {
            DropCollectionReq dropReq = DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            
            getMilvusClient().dropCollection(dropReq);
            log.info("Dropped collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Failed to drop collection {}: {}", collectionName, e.getMessage());
            throw new RuntimeException("Failed to drop collection: " + e.getMessage(), e);
        }
    }
    
    private KnowledgeDTO toDTO(RagKnowledgeBase kb) {
        KnowledgeDTO dto = new KnowledgeDTO();
        BeanUtils.copyProperties(kb, dto);
        
        if (kb.getEmbeddingModelId() != null) {
            RagEmbeddingModel model = embeddingModelMapper.findById(kb.getEmbeddingModelId());
            if (model != null) {
                dto.setEmbeddingModelName(model.getModelName());
                dto.setEmbeddingDimension(model.getDimension());
            }
        }
        
        return dto;
    }
}
