package com.rain.zhixueai.controller.rag;

import com.rain.zhixueai.dto.rag.KnowledgeDTO;
import com.rain.zhixueai.service.rag.KnowledgeService;
import com.rain.zhixuecommon.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag/knowledge")
public class KnowledgeController {
    
    @Autowired
    private KnowledgeService knowledgeService;
    
    @PostMapping
    public Result create(@RequestBody KnowledgeDTO dto) {
        try {
            KnowledgeDTO created = knowledgeService.create(dto);
            log.info("创建知识库成功: {}", created.getName());
            return Result.success(created);
        } catch (Exception e) {
            log.error("创建知识库失败", e);
            return Result.wrong("创建知识库失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody KnowledgeDTO dto) {
        try {
            dto.setId(id);
            KnowledgeDTO updated = knowledgeService.update(dto);
            log.info("更新知识库成功: {}", updated.getName());
            return Result.success(updated);
        } catch (Exception e) {
            log.error("更新知识库失败", e);
            return Result.wrong("更新知识库失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        try {
            knowledgeService.delete(id);
            log.info("删除知识库成功: {}", id);
            return Result.success(null, "删除成功");
        } catch (Exception e) {
            log.error("删除知识库失败", e);
            return Result.wrong("删除知识库失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        try {
            KnowledgeDTO dto = knowledgeService.getById(id);
            if (dto == null) {
                return Result.wrong("知识库不存在");
            }
            return Result.success(dto);
        } catch (Exception e) {
            log.error("获取知识库失败", e);
            return Result.wrong("获取知识库失败: " + e.getMessage());
        }
    }
    
    @GetMapping
    public Result list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<KnowledgeDTO> list = knowledgeService.list(keyword, page, size);
            Map<String, Object> result = new HashMap<>();
            result.put("items", list);
            result.put("page", page);
            result.put("size", size);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取知识库列表失败", e);
            return Result.wrong("获取知识库列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/enabled")
    public Result listEnabled() {
        try {
            List<KnowledgeDTO> list = knowledgeService.listEnabled();
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取启用的知识库列表失败", e);
            return Result.wrong("获取启用的知识库列表失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/toggle")
    public Result toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        try {
            knowledgeService.setStatus(id, enabled);
            log.info("切换知识库状态: {} -> {}", id, enabled);
            return Result.success(null, enabled ? "知识库已启用" : "知识库已禁用");
        } catch (Exception e) {
            log.error("切换知识库状态失败", e);
            return Result.wrong("切换知识库状态失败: " + e.getMessage());
        }
    }
}
