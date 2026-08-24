package com.rain.zhixueadmin.mapper;

import com.rain.zhixueadmin.entity.SystemLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SystemLogMapper {
    int insert(SystemLog log);
    
    SystemLog selectById(@Param("id") Long id);
    
    List<SystemLog> selectList(Map<String, Object> params);
    
    int countList(Map<String, Object> params);
}
