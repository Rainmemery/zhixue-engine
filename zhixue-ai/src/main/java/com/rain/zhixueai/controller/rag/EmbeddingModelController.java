package com.rain.zhixueai.controller.rag;

import com.rain.zhixueai.dto.rag.EmbeddingModelDTO;
import com.rain.zhixueai.service.rag.EmbeddingModelService;
import com.rain.zhixuecommon.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag/embedding-model")
public class EmbeddingModelController {
    
    @Autowired
    private EmbeddingModelService embeddingModelService;
    
    @GetMapping("/list")
    public Result list() {
        try {
            List<EmbeddingModelDTO> models = embeddingModelService.listAll();
            return Result.success(models);
        } catch (Exception e) {
            log.error("获取Embedding模型列表失败", e);
            return Result.wrong("获取Embedding模型列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/enabled")
    public Result listEnabled() {
        try {
            List<EmbeddingModelDTO> models = embeddingModelService.listEnabled();
            return Result.success(models);
        } catch (Exception e) {
            log.error("获取启用的Embedding模型列表失败", e);
            return Result.wrong("获取启用的Embedding模型列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        try {
            EmbeddingModelDTO dto = embeddingModelService.getById(id);
            if (dto == null) {
                return Result.wrong("模型不存在");
            }
            return Result.success(dto);
        } catch (Exception e) {
            log.error("获取Embedding模型失败", e);
            return Result.wrong("获取Embedding模型失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/default")
    public Result getDefault() {
        try {
            EmbeddingModelDTO dto = embeddingModelService.getDefaultModel();
            if (dto == null) {
                return Result.wrong("未设置默认模型");
            }
            return Result.success(dto);
        } catch (Exception e) {
            log.error("获取默认Embedding模型失败", e);
            return Result.wrong("获取默认Embedding模型失败: " + e.getMessage());
        }
    }
    
    @PostMapping
    public Result create(@RequestBody EmbeddingModelDTO dto) {
        try {
            EmbeddingModelDTO created = embeddingModelService.create(dto);
            log.info("创建Embedding模型成功: {}", created.getModelName());
            return Result.success(created);
        } catch (Exception e) {
            log.error("创建Embedding模型失败", e);
            return Result.wrong("创建Embedding模型失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody EmbeddingModelDTO dto) {
        try {
            dto.setId(id);
            EmbeddingModelDTO updated = embeddingModelService.update(dto);
            log.info("更新Embedding模型成功: {}", updated.getModelName());
            return Result.success(updated);
        } catch (Exception e) {
            log.error("更新Embedding模型失败", e);
            return Result.wrong("更新Embedding模型失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        try {
            embeddingModelService.delete(id);
            log.info("删除Embedding模型成功: {}", id);
            return Result.success(null, "删除成功");
        } catch (Exception e) {
            log.error("删除Embedding模型失败", e);
            return Result.wrong("删除Embedding模型失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/set-default")
    public Result setDefault(@PathVariable Long id) {
        try {
            embeddingModelService.setDefault(id);
            log.info("设置默认Embedding模型: {}", id);
            return Result.success(null, "设置默认模型成功");
        } catch (Exception e) {
            log.error("设置默认Embedding模型失败", e);
            return Result.wrong("设置默认Embedding模型失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/toggle")
    public Result toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        try {
            embeddingModelService.setStatus(id, enabled);
            log.info("切换Embedding模型状态: {} -> {}", id, enabled);
            return Result.success(null, enabled ? "模型已启用" : "模型已禁用");
        } catch (Exception e) {
            log.error("切换Embedding模型状态失败", e);
            return Result.wrong("切换Embedding模型状态失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/validate")
    public Result validateModel(@RequestBody Map<String, String> params) {
        try {
            String modelName = params.get("modelName");
            if (modelName == null || modelName.isEmpty()) {
                return Result.wrong("模型名称不能为空");
            }
            
            Map<String, Object> result = embeddingModelService.validateModel(modelName);
            return Result.success(result);
        } catch (Exception e) {
            log.error("验证Embedding模型失败", e);
            return Result.wrong("验证Embedding模型失败: " + e.getMessage());
        }
    }
}
