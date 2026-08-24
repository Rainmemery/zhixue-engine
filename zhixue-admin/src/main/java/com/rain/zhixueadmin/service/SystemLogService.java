package com.rain.zhixueadmin.service;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.SystemLog;

import java.time.LocalDateTime;

public interface SystemLogService {
    void createLog(SystemLog log);
    
    SystemLog getLogById(Long id);
    
    PageResult<SystemLog> getLogList(Long userId, String username, String action, String status, 
                                      LocalDateTime startTime, LocalDateTime endTime, Integer page, Integer size);
}
