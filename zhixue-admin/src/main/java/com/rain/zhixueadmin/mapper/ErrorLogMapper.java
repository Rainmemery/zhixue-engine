package com.rain.zhixueadmin.mapper;

import com.rain.zhixueadmin.entity.ErrorLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ErrorLogMapper {
    int insert(ErrorLog log);
    
    ErrorLog findById(@Param("id") Long id);
    
    List<ErrorLog> findAll(Map<String, Object> params);
    
    List<ErrorLog> findByUserId(@Param("userId") Long userId);
    
    List<ErrorLog> findByDateRange(@Param("startTime") LocalDateTime startTime, 
                                    @Param("endTime") LocalDateTime endTime);
    
    int countList(Map<String, Object> params);
    
    int deleteOldLogs(@Param("beforeTime") LocalDateTime beforeTime);
}
