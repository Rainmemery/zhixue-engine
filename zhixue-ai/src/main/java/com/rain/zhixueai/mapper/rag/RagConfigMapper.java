package com.rain.zhixueai.mapper.rag;

import com.rain.zhixueai.entity.rag.RagConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagConfigMapper {
    
    List<RagConfig> findAll();
    
    List<RagConfig> findByType(@Param("configType") String configType);
    
    RagConfig findByKey(@Param("configType") String configType, @Param("configKey") String configKey);
    
    int insert(RagConfig config);
    
    int update(RagConfig config);
    
    int delete(@Param("id") Long id);
    
    int deleteByKey(@Param("configType") String configType, @Param("configKey") String configKey);
    
    int upsert(RagConfig config);
}
