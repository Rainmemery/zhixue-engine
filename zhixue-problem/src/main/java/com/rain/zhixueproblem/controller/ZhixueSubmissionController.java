package com.rain.zhixueproblem.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixueproblem.service.ZhixueSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class ZhixueSubmissionController {

    private final ZhixueSubmissionService zhixueSubmissionService;

    @GetMapping
    public Result getSubmissionList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String status) {
        return Result.success(zhixueSubmissionService.getSubmissionList(page, size, problemId, userId, language, status));
    }

    @GetMapping("/{id}")
    public Result getSubmissionById(@PathVariable Long id) {
        return Result.success(zhixueSubmissionService.getSubmissionById(id));
    }

    @GetMapping("/stats/user/{userId}")
    public Result getUserSubmissionStats(@PathVariable Long userId) {
        return Result.success(zhixueSubmissionService.getUserSubmissionStats(userId));
    }

    @GetMapping("/stats/problem/{problemId}")
    public Result getProblemSubmissionStats(@PathVariable Long problemId) {
        return Result.success(zhixueSubmissionService.getProblemSubmissionStats(problemId));
    }
}
