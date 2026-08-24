package com.rain.zhixueai.mapper.rag;

import com.rain.zhixueai.entity.rag.RagDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagDocumentMapper {
    
    List<RagDocument> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    List<RagDocument> findByKnowledgeBaseIdWithPaging(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);
    
    int countByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    RagDocument findById(@Param("id") Long id);
    
    int insert(RagDocument document);
    
    int update(RagDocument document);
    
    int delete(@Param("id") Long id);
    
    int deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("errorMessage") String errorMessage);
    
    int incrementChunkCount(@Param("id") Long id, @Param("count") Integer count);
    
    List<RagDocument> findByStatus(@Param("status") Integer status);
}
