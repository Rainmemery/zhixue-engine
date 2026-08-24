package com.rain.zhixueai.agent.generation;

import com.rain.zhixueai.agent.dto.ProblemGenerationRequest;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import com.rain.zhixueai.agent.tool.impl.ProblemGenerateTool;
import com.rain.zhixueai.agent.tool.impl.TestDataGenerateTool;
import com.rain.zhixueai.agent.tool.impl.ProblemValidateTool;
import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemGenerationPipeline {

    private final GenerationStatusTracker statusTracker;
    private final ProblemGenerateTool problemGenerateTool;
    private final TestDataGenerateTool testDataGenerateTool;
    private final ProblemValidateTool problemValidateTool;

    @Autowired
    @Qualifier("problemGenerationExecutor")
    private Executor problemGenerationExecutor;

    @Autowired
    private RestTemplate restTemplate;

    private static final int MAX_RETRIES = 2;
    private static final int MAX_REGENERATIONS = 1;
    private static final long TIMEOUT_MS = 120_000;

    public String submitGenerationTask(ProblemGenerationRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        statusTracker.initialize(taskId, request.getUserId());

        ProblemGenerationTask task = ProblemGenerationTask.builder()
            .taskId(taskId)
            .request(request)
            .status(GenerationStatusTracker.GenerationStatus.QUEUED)
            .createdAt(LocalDateTime.now())
            .build();

        problemGenerationExecutor.execute(() -> {
            long startTime = System.currentTimeMillis();
            try {
                executePipeline(taskId, request, startTime);
            } catch (Exception e) {
                log.error("Pipeline execution failed for taskId={}", taskId, e);
                statusTracker.setError(taskId, "流水线执行失败: " + e.getMessage());
            }
        });

        return taskId;
    }

    public GenerationStatusTracker.GenerationState getTaskStatus(String taskId) {
        return statusTracker.get(taskId);
    }

    private void executePipeline(String taskId, ProblemGenerationRequest request, long startTime) {
        Object problemData = null;
        Object testData = null;

        for (int regenCount = 0; regenCount <= MAX_REGENERATIONS; regenCount++) {
            if (checkTimeout(startTime, taskId)) return;

            problemData = executePhase1(taskId, request);
            if (problemData == null) {
                statusTracker.setError(taskId, "题目生成失败，重试次数已用尽");
                return;
            }

            if (checkTimeout(startTime, taskId)) return;

            testData = executePhase2(taskId, problemData);
            if (testData == null) {
                statusTracker.setError(taskId, "测试数据生成失败，重试次数已用尽");
                return;
            }

            if (checkTimeout(startTime, taskId)) return;

            boolean validated = executePhase3(taskId, problemData, testData);
            if (validated) {
                break;
            }

            if (regenCount >= MAX_REGENERATIONS) {
                statusTracker.setError(taskId, "题目验证失败，重新生成次数已用尽");
                return;
            }

            log.info("Validation failed for taskId={}, regenerating (attempt {})", taskId, regenCount + 1);
        }

        if (checkTimeout(startTime, taskId)) return;

        executePhase4(taskId, problemData, testData, request);
    }

    private Object executePhase1(String taskId, ProblemGenerationRequest request) {
        statusTracker.updateStatus(taskId, GenerationStatusTracker.GenerationStatus.GENERATING_PROBLEM);
        statusTracker.updatePhase(taskId, 1);

        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("knowledgePoint", request.getKnowledgePoint());
                params.put("difficulty", request.getDifficulty());
                params.put("problemType", request.getProblemType());
                params.put("language", request.getLanguage());
                params.put("context", request.getContext());

                ToolExecutionContext context = ToolExecutionContext.builder()
                    .userId(request.getUserId()).build();

                ToolCallResult result = problemGenerateTool.execute(params, context);
                if (result.isSuccess() && result.getData() != null) {
                    return result.getData();
                }

                log.warn("Problem generation attempt {} failed for taskId={}: {}", retry + 1, taskId, result.getError());
                if (retry < MAX_RETRIES) {
                    statusTracker.incrementRetry(taskId);
                }
            } catch (Exception e) {
                log.error("Problem generation attempt {} failed for taskId={}", retry + 1, taskId, e);
                if (retry < MAX_RETRIES) {
                    statusTracker.incrementRetry(taskId);
                }
            }
        }
        return null;
    }

    private Object executePhase2(String taskId, Object problemData) {
        statusTracker.updateStatus(taskId, GenerationStatusTracker.GenerationStatus.GENERATING_TEST_DATA);
        statusTracker.updatePhase(taskId, 2);

        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("problemData", problemData);

                ToolExecutionContext context = ToolExecutionContext.builder().build();

                ToolCallResult result = testDataGenerateTool.execute(params, context);
                if (result.isSuccess() && result.getData() != null) {
                    return result.getData();
                }

                log.warn("Test data generation attempt {} failed for taskId={}: {}", retry + 1, taskId, result.getError());
                if (retry < MAX_RETRIES) {
                    statusTracker.incrementRetry(taskId);
                }
            } catch (Exception e) {
                log.error("Test data generation attempt {} failed for taskId={}", retry + 1, taskId, e);
                if (retry < MAX_RETRIES) {
                    statusTracker.incrementRetry(taskId);
                }
            }
        }
        return null;
    }

    private boolean executePhase3(String taskId, Object problemData, Object testData) {
        statusTracker.updateStatus(taskId, GenerationStatusTracker.GenerationStatus.VALIDATING);
        statusTracker.updatePhase(taskId, 3);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("problemData", problemData);
            params.put("testCases", testData);

            ToolExecutionContext context = ToolExecutionContext.builder().build();

            ToolCallResult result = problemValidateTool.execute(params, context);
            if (result.isSuccess() && result.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.getData();
                return Boolean.TRUE.equals(data.get("overallPassed"));
            }
            return false;
        } catch (Exception e) {
            log.error("Validation failed for taskId={}", taskId, e);
            return false;
        }
    }

    private void executePhase4(String taskId, Object problemData, Object testData, ProblemGenerationRequest request) {
        statusTracker.updateStatus(taskId, GenerationStatusTracker.GenerationStatus.SAVING);
        statusTracker.updatePhase(taskId, 4);

        try {
            if (!(problemData instanceof Map)) {
                statusTracker.setError(taskId, "题目数据格式错误");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) problemData;
            @SuppressWarnings("unchecked")
            Map<String, Object> problem = (Map<String, Object>) data.get("problem");

            if (problem == null) {
                statusTracker.setError(taskId, "题目数据中缺少problem字段");
                return;
            }

            List<String> missingFields = new ArrayList<>();
            if (problem.get("title") == null || problem.get("title").toString().isEmpty()) {
                missingFields.add("title");
            }
            if (problem.get("description") == null || problem.get("description").toString().isEmpty()) {
                missingFields.add("description");
            }
            if (problem.get("difficulty") == null || problem.get("difficulty").toString().isEmpty()) {
                missingFields.add("difficulty");
            }
            if (!missingFields.isEmpty()) {
                log.error("题目数据不完整：缺少必要字段 {}", missingFields);
                statusTracker.setError(taskId, "题目数据不完整：缺少必要字段 " + missingFields);
                return;
            }

            Map<String, Object> createBody = new LinkedHashMap<>();
            createBody.put("title", problem.get("title"));
            createBody.put("description", problem.get("description"));
            createBody.put("inputDescription", problem.get("inputDescription"));
            createBody.put("outputDescription", problem.get("outputDescription"));
            createBody.put("hint", problem.get("hint"));
            createBody.put("difficulty", problem.get("difficulty"));
            createBody.put("problemType", problem.getOrDefault("problemType", "traditional"));
            createBody.put("timeLimitMs", problem.getOrDefault("timeLimitMs", 2000));
            createBody.put("memoryLimitMb", problem.getOrDefault("memoryLimitMb", 256));
            createBody.put("templateCode", problem.get("templateCode"));
            createBody.put("solutionCode", problem.get("solutionCode"));
            createBody.put("isPublic", true);
            createBody.put("createdBy", request.getUserId());

            Object samplesObj = problem.get("samples");
            if (samplesObj instanceof List) {
                createBody.put("samples", samplesObj);
            }

            Object tagIdsObj = problem.get("tagIds");
            if (tagIdsObj instanceof List) {
                createBody.put("tagIds", tagIdsObj);
            }

            log.info("[Pipeline] Phase4 saving problem: taskId={}, title={}, fields={}",
                taskId, problem.get("title"), createBody.keySet());

            String url = "http://zhixue-problem/api/v1/admin/problems";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, createBody, Map.class);

            if (response == null) {
                log.error("[Pipeline] Phase4 save failed: response is null, taskId={}", taskId);
                statusTracker.setError(taskId, "题目入库失败: 服务端返回空响应");
                return;
            }

            Object problemId = null;
            if (response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseData = (Map<String, Object>) response.get("data");
                Object problemIdObj = responseData.get("id");
                if (problemIdObj != null) {
                    problemId = ((Number) problemIdObj).longValue();
                    statusTracker.setProblemId(taskId, (Long) problemId);
                }
            }

            log.info("[Pipeline] Phase4 save result: response={}, problemId={}", response, problemId);

            if (problemId == null) {
                log.error("[Pipeline] Phase4 save failed: no problemId returned, taskId={}, response={}", taskId, response);
                statusTracker.setError(taskId, "题目入库失败: 未获取到题目ID");
                return;
            }

            statusTracker.updateStatus(taskId, GenerationStatusTracker.GenerationStatus.COMPLETED);
            log.info("Problem generation pipeline completed for taskId={}, problemId={}", taskId, problemId);

        } catch (Exception e) {
            log.error("[Pipeline] Phase4 save failed for taskId={}, error={}", taskId, e.getMessage(), e);
            statusTracker.setError(taskId, "题目入库失败: " + e.getMessage());
        }
    }

    private boolean checkTimeout(long startTime, String taskId) {
        if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
            statusTracker.setError(taskId, "出题任务超时（超过120秒）");
            return true;
        }
        return false;
    }
}
