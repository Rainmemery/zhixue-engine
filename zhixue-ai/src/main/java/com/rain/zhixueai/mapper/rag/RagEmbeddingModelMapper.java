package com.rain.zhixueai.mapper.rag;

import com.rain.zhixueai.entity.rag.RagEmbeddingModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagEmbeddingModelMapper {
    
    List<RagEmbeddingModel> findAll();
    
    List<RagEmbeddingModel> findAllEnabled();
    
    RagEmbeddingModel findById(@Param("id") Long id);
    
    RagEmbeddingModel findByModelName(@Param("modelName") String modelName);
    
    RagEmbeddingModel findDefault();
    
    int insert(RagEmbeddingModel model);
    
    int update(RagEmbeddingModel model);
    
    int delete(@Param("id") Long id);
    
    int setDefault(@Param("id") Long id);
    
    int clearDefault();
    
    int updateStatus(@Param("id") Long id, @Param("status") Boolean status);
}
