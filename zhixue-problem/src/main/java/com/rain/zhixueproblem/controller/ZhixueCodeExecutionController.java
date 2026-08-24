package com.rain.zhixueproblem.controller;

import com.alibaba.fastjson2.JSON;
import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixuecommon.utils.JwtUtil;
import com.rain.zhixueproblem.entity.ZhixueJudgeTask;
import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.entity.ZhixueProblemSample;
import com.rain.zhixueproblem.entity.ZhixueSubmission;
import com.rain.zhixueproblem.judge.JudgeDispatcher;
import com.rain.zhixueproblem.mapper.ZhixueJudgeTaskMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemSampleMapper;
import com.rain.zhixueproblem.mapper.ZhixueSubmissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
@Slf4j
public class ZhixueCodeExecutionController {

    private final ZhixueSubmissionMapper zhixueSubmissionMapper;
    private final ZhixueJudgeTaskMapper zhixueJudgeTaskMapper;
    private final ZhixueProblemSampleMapper zhixueProblemSampleMapper;
    private final ZhixueProblemMapper zhixueProblemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;
    private final JudgeDispatcher judgeDispatcher;

    @PostMapping("/{problemId}/submit")
    public Result submitCode(@PathVariable Long problemId, @RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = extractUserId(request);
        String language = body.get("language");
        String code = body.get("code");

        ZhixueSubmission submission = ZhixueSubmission.builder()
                .userId(userId)
                .problemId(problemId)
                .language(language)
                .code(code)
                .codeLength(code != null ? code.length() : 0)
                .status("pending")
                .score(0.0)
                .passedCount(0)
                .totalCount(0)
                .isContest(false)
                .createdAt(LocalDateTime.now())
                .build();
        zhixueSubmissionMapper.insert(submission);

        ZhixueJudgeTask judgeTask = ZhixueJudgeTask.builder()
                .submissionId(submission.getId())
                .status("queued")
                .priority(0)
                .retryCount(0)
                .maxRetry(3)
                .createdAt(LocalDateTime.now())
                .build();
        zhixueJudgeTaskMapper.insert(judgeTask);

        Map<String, Object> taskMap = new HashMap<>();
        taskMap.put("taskId", judgeTask.getId());
        taskMap.put("submissionId", submission.getId());
        taskMap.put("problemId", problemId);
        taskMap.put("language", language);
        taskMap.put("code", code);
        stringRedisTemplate.opsForList().leftPush("judge:queue", JSON.toJSONString(taskMap));

        Map<String, Object> result = new HashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", "pending");
        result.put("message", "提交成功，等待判题");
        result.put("engine", judgeDispatcher.isDockerAvailable() ? "docker" : "local");
        return Result.success(result);
    }

    @PostMapping("/{problemId}/run")
    public Result runCode(@PathVariable Long problemId, @RequestBody Map<String, Object> body) {
        String language = (String) body.get("language");
        String code = (String) body.get("code");
        String customInput = (String) body.get("customInput");
        String customExpectedOutput = (String) body.get("customExpectedOutput");

        ZhixueProblem problem = zhixueProblemMapper.selectById(problemId);
        int timeLimitMs = problem != null && problem.getTimeLimitMs() != null ? problem.getTimeLimitMs() : 2000;

        Map<String, Object> result;

        if (customInput != null && !customInput.isEmpty()) {
            result = judgeDispatcher.dispatchRun(language, code, customInput, customExpectedOutput, timeLimitMs);
        } else {
            List<ZhixueProblemSample> samples = zhixueProblemSampleMapper.selectByProblemId(problemId);
            if (samples.isEmpty()) {
                result = new HashMap<>();
                result.put("status", "completed");
                result.put("results", new ArrayList<>());
                result.put("passedCount", 0);
                result.put("totalCount", 0);
            } else {
                result = judgeDispatcher.dispatchRunWithSamples(language, code, samples, timeLimitMs);
            }
        }

        result.put("engine", judgeDispatcher.isDockerAvailable() ? "docker" : "local");
        return Result.success(result);
    }

    private Long extractUserId(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String userIdStr = jwtUtil.getUserIdFromToken(token);
                if (userIdStr != null) {
                    return Long.parseLong(userIdStr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract userId from token", e);
        }
        return 1L;
    }
}
