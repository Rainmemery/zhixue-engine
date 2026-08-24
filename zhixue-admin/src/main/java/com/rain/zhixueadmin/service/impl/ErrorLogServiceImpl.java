package com.rain.zhixueadmin.service.impl;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.ErrorLog;
import com.rain.zhixueadmin.mapper.ErrorLogMapper;
import com.rain.zhixueadmin.service.ErrorLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ErrorLogServiceImpl implements ErrorLogService {
    
    private final ErrorLogMapper errorLogMapper;

    @Override
    public void createLog(ErrorLog log) {
        errorLogMapper.insert(log);
    }

    @Override
    public ErrorLog getLogById(Long id) {
        return errorLogMapper.findById(id);
    }

    @Override
    public PageResult<ErrorLog> getLogList(Long userId, String username, String requestUrl, String requestMethod,
                                            Integer statusCode, LocalDateTime startTime, LocalDateTime endTime,
                                            Integer page, Integer size) {
        HashMap<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("userId", userId);
        params.put("username", username);
        params.put("requestUrl", requestUrl);
        params.put("requestMethod", requestMethod);
        params.put("statusCode", statusCode);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        
        List<ErrorLog> items = errorLogMapper.findAll(params);
        int total = errorLogMapper.countList(params);
        
        return PageResult.<ErrorLog>builder()
                .items(items)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }

    @Override
    public List<ErrorLog> getLogsByUserId(Long userId) {
        return errorLogMapper.findByUserId(userId);
    }

    @Override
    public List<ErrorLog> getLogsByDateRange(LocalDateTime startTime, LocalDateTime endTime) {
        return errorLogMapper.findByDateRange(startTime, endTime);
    }

    @Override
    public int cleanOldLogs(int daysToKeep) {
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(daysToKeep);
        return errorLogMapper.deleteOldLogs(beforeTime);
    }
}
