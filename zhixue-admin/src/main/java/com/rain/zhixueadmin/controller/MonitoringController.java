package com.rain.zhixueadmin.controller;

import com.rain.zhixueadmin.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) monitoringService.getSystemDetail();
        return Map.of("code", 200, "message", "success", "data", metrics);
    }

    @GetMapping("/events")
    public Map<String, Object> getEvents(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        
        List<Map<String, Object>> events = new ArrayList<>();
        
        events.add(Map.of(
            "id", 1,
            "type", "INFO",
            "message", "系统启动完成",
            "timestamp", LocalDateTime.now().minusHours(2).toString(),
            "source", "system"
        ));
        
        events.add(Map.of(
            "id", 2,
            "type", "INFO",
            "message", "数据库连接池初始化完成",
            "timestamp", LocalDateTime.now().minusHours(1).minusMinutes(30).toString(),
            "source", "database"
        ));
        
        events.add(Map.of(
            "id", 3,
            "type", "WARNING",
            "message", "内存使用率超过70%",
            "timestamp", LocalDateTime.now().minusMinutes(30).toString(),
            "source", "monitor"
        ));
        
        return Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of(
                "items", events,
                "total", events.size(),
                "page", page,
                "size", size
            )
        );
    }
}
