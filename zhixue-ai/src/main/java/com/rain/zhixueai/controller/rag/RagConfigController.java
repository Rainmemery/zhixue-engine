package com.rain.zhixueai.controller.rag;

import com.rain.zhixueai.service.rag.RagConfigService;
import com.rain.zhixuecommon.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag/config")
public class RagConfigController {
    
    @Autowired
    private RagConfigService configService;
    
    @GetMapping("/global")
    public Result getGlobalConfig() {
        try {
            Map<String, Object> config = configService.getGlobalConfig();
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取全局配置失败", e);
            return Result.wrong("获取全局配置失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/global")
    public Result updateGlobalConfig(@RequestBody Map<String, Object> config) {
        try {
            configService.updateGlobalConfig(config);
            return Result.success(null, "更新全局配置成功");
        } catch (Exception e) {
            log.error("更新全局配置失败", e);
            return Result.wrong("更新全局配置失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/global/toggle")
    public Result toggleRag(@RequestParam boolean enabled) {
        try {
            configService.setGlobalEnabled(enabled);
            return Result.success(null, enabled ? "RAG功能已启用" : "RAG功能已禁用");
        } catch (Exception e) {
            log.error("切换RAG状态失败", e);
            return Result.wrong("切换RAG状态失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/modules")
    public Result getModuleConfigs() {
        try {
            List<Map<String, Object>> configs = configService.getModuleConfigs();
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取模块配置失败", e);
            return Result.wrong("获取模块配置失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/modules/{moduleCode}")
    public Result getModuleConfig(@PathVariable String moduleCode) {
        try {
            Map<String, Object> config = configService.getModuleConfig(moduleCode);
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取模块配置失败: {}", moduleCode, e);
            return Result.wrong("获取模块配置失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/modules/{moduleCode}")
    public Result updateModuleConfig(
            @PathVariable String moduleCode,
            @RequestBody Map<String, Object> config) {
        try {
            configService.updateModuleConfig(moduleCode, config);
            return Result.success(null, "更新模块配置成功");
        } catch (Exception e) {
            log.error("更新模块配置失败: {}", moduleCode, e);
            return Result.wrong("更新模块配置失败: " + e.getMessage());
        }
    }
}
