package com.rain.zhixueai.controller.rag;

import com.rain.zhixueai.dto.rag.RetrievalRequest;
import com.rain.zhixueai.dto.rag.RetrievalResult;
import com.rain.zhixueai.service.rag.RetrievalService;
import com.rain.zhixuecommon.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag/retrieval")
public class RetrievalController {
    
    @Autowired
    private RetrievalService retrievalService;
    
    @PostMapping("/search")
    public Result search(@RequestBody RetrievalRequest request) {
        try {
            if (request.getQuery() == null || request.getQuery().isEmpty()) {
                return Result.wrong("查询内容不能为空");
            }
            
            if (request.getKnowledgeBaseIds() == null || request.getKnowledgeBaseIds().isEmpty()) {
                return Result.wrong("请选择至少一个知识库");
            }
            
            RetrievalResult result = retrievalService.search(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("检索失败", e);
            return Result.wrong("检索失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/context")
    public Result buildContext(@RequestBody RetrievalRequest request) {
        try {
            if (request.getQuery() == null || request.getQuery().isEmpty()) {
                return Result.wrong("查询内容不能为空");
            }
            
            if (request.getKnowledgeBaseIds() == null || request.getKnowledgeBaseIds().isEmpty()) {
                return Result.wrong("请选择至少一个知识库");
            }
            
            var context = retrievalService.buildContext(request.getQuery(), request.getKnowledgeBaseIds());
            return Result.success(context);
        } catch (Exception e) {
            log.error("构建上下文失败", e);
            return Result.wrong("构建上下文失败: " + e.getMessage());
        }
    }
}
