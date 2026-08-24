package com.rain.zhixueadmin.service;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.ErrorLog;

import java.time.LocalDateTime;
import java.util.List;

public interface ErrorLogService {
    void createLog(ErrorLog log);
    
    ErrorLog getLogById(Long id);
    
    PageResult<ErrorLog> getLogList(Long userId, String username, String requestUrl, String requestMethod,
                                     Integer statusCode, LocalDateTime startTime, LocalDateTime endTime,
                                     Integer page, Integer size);
    
    List<ErrorLog> getLogsByUserId(Long userId);
    
    List<ErrorLog> getLogsByDateRange(LocalDateTime startTime, LocalDateTime endTime);
    
    int cleanOldLogs(int daysToKeep);
}
