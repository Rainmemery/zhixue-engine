package com.rain.zhixueadmin.controller;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.SystemLog;
import com.rain.zhixueadmin.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
public class SystemLogController {
    
    private final SystemLogService systemLogService;

    @GetMapping
    public Map<String, Object> getLogList(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult<SystemLog> result = systemLogService.getLogList(userId, username, action, status, startTime, endTime, page, size);
        return Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of(
                "items", result.getItems(),
                "total", result.getTotal(),
                "page", result.getPage(),
                "size", result.getSize()
            )
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getLogById(@PathVariable Long id) {
        SystemLog log = systemLogService.getLogById(id);
        if (log == null) {
            return Map.of("code", 404, "message", "日志不存在");
        }
        return Map.of("code", 200, "message", "success", "data", log);
    }
}
