package com.rain.zhixueai.mapper.rag;

import com.rain.zhixueai.entity.rag.RagChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagChunkMapper {
    
    List<RagChunk> findByDocumentId(@Param("documentId") Long documentId);
    
    List<RagChunk> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    RagChunk findById(@Param("id") Long id);
    
    RagChunk findByVectorId(@Param("vectorId") String vectorId);
    
    int insert(RagChunk chunk);
    
    int batchInsert(@Param("chunks") List<RagChunk> chunks);
    
    int delete(@Param("id") Long id);
    
    int deleteByDocumentId(@Param("documentId") Long documentId);
    
    int deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    int updateVectorId(@Param("id") Long id, @Param("vectorId") String vectorId);
    
    int countByDocumentId(@Param("documentId") Long documentId);
    
    int countByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
