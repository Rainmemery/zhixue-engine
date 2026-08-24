package com.rain.zhixueproblem.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.service.ZhixueProblemService;
import com.rain.zhixueproblem.service.ZhixueProblemCategoryService;
import com.rain.zhixueproblem.service.ZhixueProblemTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
public class ZhixueProblemController {

    private final ZhixueProblemService zhixueProblemService;
    private final ZhixueProblemCategoryService zhixueProblemCategoryService;
    private final ZhixueProblemTagService zhixueProblemTagService;

    @GetMapping
    public Result getProblemList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long userId) {
        return Result.success(zhixueProblemService.getProblemList(page, size, difficulty, categoryId, tagId, problemType, search, sort, userId));
    }

    @GetMapping("/{id}")
    public Result getProblemDetail(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return Result.success(zhixueProblemService.getProblemDetail(id, userId));
    }

    @GetMapping("/categories")
    public Result getAllCategories() {
        return Result.success(zhixueProblemCategoryService.getAllCategories());
    }

    @GetMapping("/tags")
    public Result getAllTags() {
        return Result.success(zhixueProblemTagService.getAllTags());
    }

    @PostMapping("/agent/recommend")
    public Result recommendProblems(@RequestBody Map<String, Object> body) {
        try {
            Long userId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
            String difficulty = (String) body.get("difficulty");
            String knowledgePoint = (String) body.get("knowledgePoint");
            String strategy = (String) body.getOrDefault("strategy", "weakness");
            boolean excludeSolved = Boolean.TRUE.equals(body.get("excludeSolved"));
            int limit = body.containsKey("limit") ? ((Number) body.get("limit")).intValue() : 3;
            Long categoryId = body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null;

            List<ZhixueProblem> problems = zhixueProblemService.findProblemsForRecommend(
                userId, difficulty, knowledgePoint, strategy, excludeSolved, limit, categoryId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (ZhixueProblem p : problems) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", p.getId());
                item.put("title", p.getTitle());
                item.put("difficulty", p.getDifficulty());
                item.put("problemType", p.getProblemType());
                item.put("acceptanceRate", p.getAcceptanceRate());
                item.put("tags", p.getTags());
                result.add(item);
            }

            return Result.success(Map.of("problems", result));
        } catch (Exception e) {
            return Result.wrong("推荐题目失败: " + e.getMessage());
        }
    }

    @GetMapping("/agent/user-status/{userId}")
    public Result getUserStatus(@PathVariable Long userId) {
        try {
            Map<String, Object> status = zhixueProblemService.getUserProblemStatus(userId);
            return Result.success(status);
        } catch (Exception e) {
            return Result.wrong("获取用户解题状态失败: " + e.getMessage());
        }
    }

    @GetMapping("/agent/wrong-questions/{userId}")
    public Result getWrongQuestions(@PathVariable Long userId) {
        try {
            Map<String, Object> wrongData = zhixueProblemService.getUserWrongQuestions(userId);
            return Result.success(wrongData);
        } catch (Exception e) {
            return Result.wrong("获取错题列表失败: " + e.getMessage());
        }
    }
}
