package com.rain.zhixueai.mapper.rag;

import com.rain.zhixueai.entity.rag.RagKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagKnowledgeBaseMapper {
    
    List<RagKnowledgeBase> findAll();
    
    List<RagKnowledgeBase> findByKeyword(@Param("keyword") String keyword);
    
    List<RagKnowledgeBase> findEnabled();
    
    List<RagKnowledgeBase> findByIds(@Param("ids") List<Long> ids);
    
    RagKnowledgeBase findById(@Param("id") Long id);
    
    int insert(RagKnowledgeBase knowledgeBase);
    
    int update(RagKnowledgeBase knowledgeBase);
    
    int delete(@Param("id") Long id);
    
    int updateStatus(@Param("id") Long id, @Param("status") Boolean status);
    
    int incrementDocumentCount(@Param("id") Long id);
    
    int decrementDocumentCount(@Param("id") Long id);
    
    int incrementChunkCount(@Param("id") Long id, @Param("count") Integer count);
    
    int decrementChunkCount(@Param("id") Long id, @Param("count") Integer count);
}
