package com.rain.zhixueproblem.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rain.zhixueproblem.entity.ZhixueJudgeTask;
import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.entity.ZhixueProblemSample;
import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import com.rain.zhixueproblem.entity.ZhixueSubmission;
import com.rain.zhixueproblem.entity.ZhixueTestcaseResult;
import com.rain.zhixueproblem.entity.ZhixueUserProblemStatus;
import com.rain.zhixueproblem.judge.model.JudgeResult;
import com.rain.zhixueproblem.judge.model.TestcaseResult;
import com.rain.zhixueproblem.mapper.ZhixueJudgeTaskMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemSampleMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemTestcaseMapper;
import com.rain.zhixueproblem.mapper.ZhixueSubmissionMapper;
import com.rain.zhixueproblem.mapper.ZhixueTestcaseResultMapper;
import com.rain.zhixueproblem.mapper.ZhixueUserProblemStatusMapper;
import com.rain.zhixuecommon.websocket.dto.DataChangeEvent;
import com.rain.zhixuecommon.websocket.service.DataSyncService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.rain.zhixueproblem.judge.config.JudgeMachineConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class JudgeWorker {

    private final StringRedisTemplate stringRedisTemplate;
    private final JudgeDispatcher judgeDispatcher;
    private final ZhixueSubmissionMapper zhixueSubmissionMapper;
    private final ZhixueJudgeTaskMapper zhixueJudgeTaskMapper;
    private final ZhixueTestcaseResultMapper zhixueTestcaseResultMapper;
    private final ZhixueUserProblemStatusMapper zhixueUserProblemStatusMapper;
    private final ZhixueProblemMapper zhixueProblemMapper;
    private final ZhixueProblemSampleMapper zhixueProblemSampleMapper;
    private final ZhixueProblemTestcaseMapper zhixueProblemTestcaseMapper;
    private final ObjectMapper objectMapper;
    private final DataSyncService dataSyncService;
    private final JudgeMachineConfig judgeMachineConfig;
    private final ContainerPoolManager containerPoolManager;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService judgePool;
    private Thread pollThread;

    @PostConstruct
    public void start() {
        running.set(true);
        int maxConcurrency = judgeMachineConfig.getMaxConcurrency();
        AtomicInteger threadIndex = new AtomicInteger(0);
        judgePool = Executors.newFixedThreadPool(maxConcurrency, r -> {
            Thread t = new Thread(r, "judge-worker-" + threadIndex.incrementAndGet());
            t.setDaemon(false);
            return t;
        });
        pollThread = new Thread(this::poll, "judge-poll");
        pollThread.start();
        log.info("Judge worker started with pool size {}, Docker available: {}", maxConcurrency, judgeDispatcher.isDockerAvailable());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
        if (judgePool != null) {
            judgePool.shutdown();
            try {
                if (!judgePool.awaitTermination(30, TimeUnit.SECONDS)) {
                    judgePool.shutdownNow();
                }
            } catch (InterruptedException e) {
                judgePool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Judge worker stopped");
    }

    private void poll() {
        while (running.get()) {
            try {
                String taskJson = stringRedisTemplate.opsForList().rightPop("judge:queue");
                if (taskJson == null) {
                    Thread.sleep(1000);
                    continue;
                }

                Map<String, Object> task = objectMapper.readValue(taskJson, Map.class);
                Long submissionId = Long.valueOf(task.get("submissionId").toString());
                Long problemId = Long.valueOf(task.get("problemId").toString());
                String language = (String) task.get("language");
                String code = (String) task.get("code");
                Long taskId = task.get("taskId") != null ? Long.valueOf(task.get("taskId").toString()) : null;

                log.info("Submitting judge task: submissionId={}, problemId={}, language={}, dockerAvailable={}",
                        submissionId, problemId, language, judgeDispatcher.isDockerAvailable());

                judgePool.submit(() -> processJudgeTask(submissionId, problemId, language, code, taskId));

                try {
                    Long queueSize = stringRedisTemplate.opsForList().size("judge:queue");
                    if (queueSize != null && queueSize > 0) {
                        int totalIdle = containerPoolManager.getTotalIdleCount();
                        if (queueSize > totalIdle) {
                            int needed = (int) Math.min(queueSize - totalIdle, 3);
                            for (String lang : new String[]{"java", "cpp", "c"}) {
                                containerPoolManager.scaleUp(lang, needed);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to check queue for scaling: {}", e.getMessage());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Judge poll error", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processJudgeTask(Long submissionId, Long problemId, String language, String code, Long taskId) {
        try {
            ZhixueSubmission judgingSubmission = new ZhixueSubmission();
            judgingSubmission.setId(submissionId);
            judgingSubmission.setStatus("judging");
            zhixueSubmissionMapper.updateById(judgingSubmission);

            try {
                ZhixueSubmission updatedSub = zhixueSubmissionMapper.selectById(submissionId);
                if (updatedSub != null) {
                    DataChangeEvent judgingEvent = DataChangeEvent.update("submission",
                            String.valueOf(submissionId), updatedSub, "zhixue-problem");
                    dataSyncService.broadcastDataChange(judgingEvent);
                    dataSyncService.notifyUser(String.valueOf(updatedSub.getUserId()), judgingEvent);
                }
            } catch (Exception e) {
                log.warn("Failed to send WebSocket notification for judging status: {}", e.getMessage());
            }

            if (taskId != null) {
                ZhixueJudgeTask runningTask = ZhixueJudgeTask.builder()
                        .id(taskId)
                        .status("running")
                        .workerId("judge-worker")
                        .startedAt(LocalDateTime.now())
                        .build();
                zhixueJudgeTaskMapper.updateById(runningTask);
            }

            ZhixueProblem problem = zhixueProblemMapper.selectById(problemId);
            if (problem == null) {
                log.error("Problem not found: {}", problemId);
                if (taskId != null) {
                    ZhixueJudgeTask failedTask = ZhixueJudgeTask.builder()
                            .id(taskId)
                            .status("failed")
                            .completedAt(LocalDateTime.now())
                            .errorMessage("Problem not found: " + problemId)
                            .build();
                    zhixueJudgeTaskMapper.updateById(failedTask);
                }
                return;
            }

            List<ZhixueProblemTestcase> testcases = zhixueProblemTestcaseMapper.selectByProblemId(problemId);
            if (testcases == null || testcases.isEmpty()) {
                List<ZhixueProblemSample> samples = zhixueProblemSampleMapper.selectByProblemId(problemId);
                testcases = new ArrayList<>();
                for (ZhixueProblemSample sample : samples) {
                    testcases.add(ZhixueProblemTestcase.builder()
                            .id(sample.getId())
                            .problemId(problemId)
                            .testcaseName("sample-" + sample.getId())
                            .inputFilePath(sample.getSampleInput())
                            .outputFilePath(sample.getSampleOutput())
                            .timeLimitMs(problem.getTimeLimitMs())
                            .memoryLimitMb(problem.getMemoryLimitMb())
                            .isSample(true)
                            .sortOrder(sample.getSortOrder())
                            .build());
                }
            }

            JudgeResult result = judgeDispatcher.dispatchJudge(submissionId, language, code, testcases, problem);

            updateSubmissionResult(submissionId, result);
            saveTestcaseResults(submissionId, result.getTestcaseResults());

            if (taskId != null) {
                ZhixueJudgeTask completedTask = ZhixueJudgeTask.builder()
                        .id(taskId)
                        .status("completed")
                        .completedAt(LocalDateTime.now())
                        .build();
                zhixueJudgeTaskMapper.updateById(completedTask);
            }

            ZhixueSubmission submission = zhixueSubmissionMapper.selectById(submissionId);
            if (submission != null) {
                updateUserProblemStatus(submission.getUserId(), problemId, submissionId, result);
                updateProblemStats(problemId, result);
            }

            log.info("Judge task completed: submissionId={}, status={}, engine={}",
                    submissionId, result.getStatus(), judgeDispatcher.isDockerAvailable() ? "Docker" : "Local");

            try {
                ZhixueSubmission updatedSubmission = zhixueSubmissionMapper.selectById(submissionId);
                if (updatedSubmission != null) {
                    DataChangeEvent event = DataChangeEvent.update("submission",
                            String.valueOf(submissionId), updatedSubmission, "zhixue-problem");
                    dataSyncService.broadcastDataChange(event);
                    dataSyncService.notifyUser(String.valueOf(updatedSubmission.getUserId()), event);
                }
            } catch (Exception e) {
                log.warn("Failed to send WebSocket notification for submission {}: {}", submissionId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("Judge worker error", e);
            if (taskId != null) {
                try {
                    ZhixueJudgeTask failedTask = ZhixueJudgeTask.builder()
                            .id(taskId)
                            .status("failed")
                            .completedAt(LocalDateTime.now())
                            .errorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown error")
                            .build();
                    zhixueJudgeTaskMapper.updateById(failedTask);
                } catch (Exception ex) {
                    log.warn("Failed to update judge task status to failed: {}", ex.getMessage());
                }
            }
        }
    }

    private void updateSubmissionResult(Long submissionId, JudgeResult result) {
        ZhixueSubmission submission = ZhixueSubmission.builder()
                .id(submissionId)
                .status(toSubmissionStatus(result.getStatus()))
                .score(result.getScore())
                .totalTimeMs(result.getTotalTimeMs())
                .maxMemoryKb(result.getMaxMemoryKb())
                .passedCount(result.getPassedCount())
                .totalCount(result.getTotalCount())
                .errorMessage(result.getErrorMessage())
                .judgedAt(LocalDateTime.now())
                .build();
        zhixueSubmissionMapper.updateById(submission);
    }

    private void saveTestcaseResults(Long submissionId, List<TestcaseResult> testcaseResults) {
        if (testcaseResults == null || testcaseResults.isEmpty()) {
            return;
        }

        List<ZhixueTestcaseResult> results = new ArrayList<>();
        for (TestcaseResult tr : testcaseResults) {
            results.add(ZhixueTestcaseResult.builder()
                    .submissionId(submissionId)
                    .testcaseId(tr.getTestcaseId())
                    .testcaseName(tr.getTestcaseName())
                    .status(toTestcaseStatus(tr.getStatus()))
                    .executionTimeMs(tr.getExecutionTimeMs())
                    .memoryUsedKb(tr.getMemoryUsedKb())
                    .actualOutput(tr.getActualOutput())
                    .errorMessage(tr.getErrorOutput())
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        zhixueTestcaseResultMapper.batchInsert(results);
    }

    private void updateUserProblemStatus(Long userId, Long problemId, Long submissionId, JudgeResult result) {
        ZhixueUserProblemStatus existing = zhixueUserProblemStatusMapper.selectByUserIdAndProblemId(userId, problemId);
        boolean isAccepted = "AC".equals(result.getStatus());

        if (existing == null) {
            ZhixueUserProblemStatus newStatus = ZhixueUserProblemStatus.builder()
                    .userId(userId)
                    .problemId(problemId)
                    .status(isAccepted ? "accepted" : "attempted")
                    .bestSubmissionId(submissionId)
                    .bestTimeMs(result.getTotalTimeMs())
                    .bestMemoryKb(result.getMaxMemoryKb())
                    .attemptCount(1)
                    .acceptedAt(isAccepted ? LocalDateTime.now() : null)
                    .updatedAt(LocalDateTime.now())
                    .build();
            zhixueUserProblemStatusMapper.insert(newStatus);
        } else {
            existing.setAttemptCount(existing.getAttemptCount() + 1);
            existing.setUpdatedAt(LocalDateTime.now());

            if (isAccepted) {
                existing.setStatus("accepted");
                existing.setAcceptedAt(existing.getAcceptedAt() != null ? existing.getAcceptedAt() : LocalDateTime.now());
                if (existing.getBestTimeMs() == null ||
                        (result.getTotalTimeMs() != null && result.getTotalTimeMs() < existing.getBestTimeMs())) {
                    existing.setBestSubmissionId(submissionId);
                    existing.setBestTimeMs(result.getTotalTimeMs());
                    existing.setBestMemoryKb(result.getMaxMemoryKb());
                }
            } else if (!"accepted".equals(existing.getStatus())) {
                existing.setStatus("attempted");
            }

            zhixueUserProblemStatusMapper.updateById(existing);
        }
    }

    private void updateProblemStats(Long problemId, JudgeResult result) {
        zhixueProblemMapper.incrementSubmitCount(problemId);

        if ("AC".equals(result.getStatus())) {
            zhixueProblemMapper.incrementAcceptedCount(problemId);
        }

        ZhixueProblem problem = zhixueProblemMapper.selectById(problemId);
        if (problem != null && problem.getSubmitCount() != null && problem.getSubmitCount() > 0) {
            double rate = (problem.getAcceptedCount() != null ? problem.getAcceptedCount() : 0) * 100.0 / problem.getSubmitCount();
            zhixueProblemMapper.updateAcceptanceRate(problemId, Math.round(rate * 100.0) / 100.0);
        }
    }

    private String toSubmissionStatus(String judgeStatus) {
        if (judgeStatus == null) return "system_error";
        return switch (judgeStatus) {
            case "AC" -> "accepted";
            case "PA" -> "partial_accepted";
            case "WA" -> "wrong_answer";
            case "TLE" -> "time_limit_exceeded";
            case "MLE" -> "memory_limit_exceeded";
            case "RE" -> "runtime_error";
            case "CE" -> "compilation_error";
            case "SE" -> "system_error";
            default -> {
                if (judgeStatus.startsWith("accepted") || judgeStatus.startsWith("partial_") ||
                    judgeStatus.startsWith("wrong") || judgeStatus.startsWith("time_") ||
                    judgeStatus.startsWith("memory_") || judgeStatus.startsWith("runtime_") ||
                    judgeStatus.startsWith("compilation_") || judgeStatus.startsWith("system_")) {
                    yield judgeStatus;
                }
                yield "system_error";
            }
        };
    }

    private String toTestcaseStatus(String judgeStatus) {
        if (judgeStatus == null) return "system_error";
        return switch (judgeStatus) {
            case "AC" -> "accepted";
            case "WA" -> "wrong_answer";
            case "TLE" -> "time_limit_exceeded";
            case "MLE" -> "memory_limit_exceeded";
            case "RE" -> "runtime_error";
            case "SE" -> "system_error";
            case "SKIP" -> "system_error";
            default -> {
                if (judgeStatus.startsWith("accepted") || judgeStatus.startsWith("wrong") ||
                    judgeStatus.startsWith("time_") || judgeStatus.startsWith("memory_") ||
                    judgeStatus.startsWith("runtime_") || judgeStatus.startsWith("system_")) {
                    yield judgeStatus;
                }
                yield "system_error";
            }
        };
    }
}
