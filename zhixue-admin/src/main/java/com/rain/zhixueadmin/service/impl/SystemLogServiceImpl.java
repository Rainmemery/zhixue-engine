package com.rain.zhixueadmin.service.impl;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.SystemLog;
import com.rain.zhixueadmin.mapper.SystemLogMapper;
import com.rain.zhixueadmin.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {
    
    private final SystemLogMapper systemLogMapper;

    @Override
    public void createLog(SystemLog log) {
        systemLogMapper.insert(log);
    }

    @Override
    public SystemLog getLogById(Long id) {
        return systemLogMapper.selectById(id);
    }

    @Override
    public PageResult<SystemLog> getLogList(Long userId, String username, String action, String status, 
                                             LocalDateTime startTime, LocalDateTime endTime, Integer page, Integer size) {
        HashMap<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("userId", userId);
        params.put("username", username);
        params.put("action", action);
        params.put("status", status);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        
        List<SystemLog> items = systemLogMapper.selectList(params);
        int total = systemLogMapper.countList(params);
        
        return PageResult.<SystemLog>builder()
                .items(items)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }
}
