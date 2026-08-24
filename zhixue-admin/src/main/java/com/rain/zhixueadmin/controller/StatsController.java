package com.rain.zhixueadmin.controller;

import com.rain.zhixueadmin.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> stats = statsService.getAllStats();
        return Map.of("code", 200, "message", "success", "data", stats);
    }
    
    @GetMapping("/users")
    public Map<String, Object> getUserStats() {
        Map<String, Object> stats = statsService.getUserStats();
        return Map.of("code", 200, "message", "success", "data", stats);
    }
    
    @GetMapping("/problems")
    public Map<String, Object> getProblemStats() {
        Map<String, Object> stats = statsService.getProblemStats();
        return Map.of("code", 200, "message", "success", "data", stats);
    }
    
    @GetMapping("/learning")
    public Map<String, Object> getLearningStats() {
        Map<String, Object> stats = statsService.getLearningStats();
        return Map.of("code", 200, "message", "success", "data", stats);
    }
    
    @GetMapping("/system")
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = statsService.getSystemStats();
        return Map.of("code", 200, "message", "success", "data", stats);
    }
}
