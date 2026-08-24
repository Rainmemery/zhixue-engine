package com.rain.zhixuelearning.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixuecommon.utils.JwtUtil;
import com.rain.zhixuelearning.entity.LearningPathSteps;
import com.rain.zhixuelearning.entity.LearningPaths;
import com.rain.zhixuelearning.service.LearningPathService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/learning/paths")
public class LearningPathController {
    @Autowired
    private LearningPathService learningPathService;
    
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/generate")
    public Result generateLearningPath(@RequestHeader(value = "Authorization", required = false) String authHeader, 
                                       @RequestBody LearningPaths learningPaths){
        String token = authHeader != null ? authHeader.replaceFirst("^Bearer\\s+", "").trim() : null;
        if (token == null || token.isEmpty()) {
            return Result.wrong("未提供认证令牌");
        }
        
        try {
            String userIdStr = jwtUtil.getUserIdFromToken(token);
            if (userIdStr == null) {
                return Result.wrong("无效的认证令牌");
            }
            learningPaths.setUserId(Long.parseLong(userIdStr));
            
            LearningPaths createdPath = learningPathService.generateLearningPath(learningPaths);
            if(createdPath == null){
                return Result.wrong("创建失败，请联系管理员");
            }
            return Result.success(createdPath);
        }catch(Exception e){
            return Result.wrong("添加学习路径失败: " + e.getMessage());
        }
    }

    @GetMapping("/recommended")
    public Result getRecommendedPaths() {
        try {
            List<LearningPaths> paths = learningPathService.getRecommendedPaths();
            return Result.success(paths);
        } catch (Exception e) {
            return Result.wrong("获取推荐路径失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public Result getUserPaths(@PathVariable Long userId) {
        try {
            List<LearningPaths> paths = learningPathService.getUserPaths(userId);
            return Result.success(paths);
        } catch (Exception e) {
            return Result.wrong("获取用户路径失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result getPathById(@PathVariable Long id) {
        try {
            LearningPaths path = learningPathService.getPathById(id);
            if (path == null) {
                return Result.wrong("路径不存在");
            }
            return Result.success(path);
        } catch (Exception e) {
            return Result.wrong("获取路径失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/start")
    public Result startPath(@PathVariable Long id) {
        try {
            LearningPaths path = learningPathService.startPath(id);
            return Result.success(path);
        } catch (Exception e) {
            return Result.wrong("开始路径失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/pause")
    public Result pausePath(@PathVariable Long id) {
        try {
            LearningPaths path = learningPathService.pausePath(id);
            return Result.success(path);
        } catch (Exception e) {
            return Result.wrong("暂停路径失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/continue")
    public Result continuePath(@PathVariable Long id) {
        try {
            LearningPaths path = learningPathService.continuePath(id);
            return Result.success(path);
        } catch (Exception e) {
            return Result.wrong("继续路径失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/complete")
    public Result completePath(@PathVariable Long id) {
        try {
            LearningPaths path = learningPathService.completePath(id);
            return Result.success(path);
        } catch (Exception e) {
            return Result.wrong("完成路径失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/progress")
    public Result updateProgress(@PathVariable Long id, @RequestBody Map<String, Object> progressData) {
        try {
            Integer stepNumber = (Integer) progressData.get("stepNumber");
            String status = (String) progressData.get("status");
            Integer score = progressData.get("score") != null ? (Integer) progressData.get("score") : null;
            Integer timeSpentMinutes = progressData.get("timeSpentMinutes") != null ? 
                (Integer) progressData.get("timeSpentMinutes") : null;
            
            LearningPathSteps step = learningPathService.updateProgress(id, stepNumber, status, score, timeSpentMinutes);
            return Result.success(step);
        } catch (Exception e) {
            return Result.wrong("更新进度失败: " + e.getMessage());
        }
    }
}
