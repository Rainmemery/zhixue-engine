package com.rain.zhixueadmin.controller;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.ErrorLog;
import com.rain.zhixueadmin.service.ErrorLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/error-logs")
@RequiredArgsConstructor
public class ErrorLogController {
    
    private final ErrorLogService errorLogService;

    @GetMapping
    public Map<String, Object> getLogList(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String requestUrl,
            @RequestParam(required = false) String requestMethod,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult<ErrorLog> result = errorLogService.getLogList(userId, username, requestUrl, requestMethod, 
                                                                  statusCode, startTime, endTime, page, size);
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
        ErrorLog log = errorLogService.getLogById(id);
        if (log == null) {
            return Map.of("code", 404, "message", "错误日志不存在");
        }
        return Map.of("code", 200, "message", "success", "data", log);
    }

    @DeleteMapping("/clean")
    public Map<String, Object> cleanOldLogs(@RequestParam(defaultValue = "30") Integer daysToKeep) {
        int deletedCount = errorLogService.cleanOldLogs(daysToKeep);
        return Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of("deletedCount", deletedCount)
        );
    }
}
