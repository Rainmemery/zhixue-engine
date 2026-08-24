package com.rain.zhixueproblem.judge;

import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.entity.ZhixueProblemSample;
import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import com.rain.zhixueproblem.judge.model.JudgeResult;
import com.rain.zhixueproblem.judge.model.TestcaseResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class JudgeDispatcher {

    private final LocalCodeRunner localCodeRunner;

    @Autowired(required = false)
    private JudgeEngine judgeEngine;

    @Autowired(required = false)
    private ContainerPoolManager containerPoolManager;

    private final AtomicBoolean dockerAvailable = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        log.info("JudgeDispatcher init - judgeEngine: {}, containerPoolManager: {}", judgeEngine != null, containerPoolManager != null);
        checkDockerAvailability();
        log.info("JudgeDispatcher initialized, docker available: {}", dockerAvailable.get());
    }

    @Scheduled(fixedDelay = 30000)
    public void scheduledDockerCheck() {
        boolean wasAvailable = dockerAvailable.get();
        checkDockerAvailability();
        boolean nowAvailable = dockerAvailable.get();
        if (wasAvailable != nowAvailable) {
            log.info("Docker availability changed: {} -> {}", wasAvailable, nowAvailable);
        }
    }

    public void checkDockerAvailability() {
        log.info("Docker availability check: judgeEngine={}, containerPoolManager={}, currentDockerAvailable={}",
                judgeEngine != null, containerPoolManager != null, dockerAvailable.get());
        try {
            if (judgeEngine != null && containerPoolManager != null) {
                containerPoolManager.ensurePoolsReady();
                dockerAvailable.set(true);
                log.info("Docker is available, pools are ready");
            } else {
                log.warn("Docker not available - judgeEngine: {}, containerPoolManager: {}",
                        judgeEngine != null, containerPoolManager != null);
                dockerAvailable.set(false);
            }
        } catch (Exception e) {
            log.warn("Docker availability check failed: {}", e.getMessage());
            dockerAvailable.set(false);
        }
    }

    public boolean isDockerAvailable() {
        return dockerAvailable.get();
    }

    public Map<String, Object> dispatchRun(String language, String code, String input,
                                            String expectedOutput, int timeLimitMs) {
        log.info("Dispatching run: dockerAvailable={}", dockerAvailable.get());
        if (dockerAvailable.get()) {
            try {
                Map<String, Object> result = runInDocker(language, code, input, expectedOutput, timeLimitMs);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Docker run failed, falling back to LocalCodeRunner: {}", e.getMessage());
            }
        }
        return runLocally(language, code, input, expectedOutput, timeLimitMs);
    }

    public Map<String, Object> dispatchRunWithSamples(String language, String code,
                                                       List<ZhixueProblemSample> samples,
                                                       int timeLimitMs) {
        if (dockerAvailable.get()) {
            try {
                Map<String, Object> result = runSamplesInDocker(language, code, samples, timeLimitMs);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Docker sample run failed, falling back to LocalCodeRunner: {}", e.getMessage());
            }
        }
        return runSamplesLocally(language, code, samples, timeLimitMs);
    }

    public JudgeResult dispatchJudge(Long submissionId, String language, String code,
                                      List<ZhixueProblemTestcase> testcases, ZhixueProblem problem) {
        log.info("Dispatching judge for submission {}: dockerAvailable={}", submissionId, dockerAvailable.get());
        if (dockerAvailable.get()) {
            try {
                JudgeResult result = judgeEngine.judge(submissionId, language, code, testcases, problem);
                if (result != null) {
                    log.info("Docker judge completed successfully for submission {}, status={}", submissionId, result.getStatus());
                    return result;
                }
            } catch (Exception e) {
                log.warn("Docker judge failed for submission {}, falling back to LocalCodeRunner: {}",
                        submissionId, e.getMessage());
            }
        }
        log.info("Using LocalCodeRunner for submission {}", submissionId);
        return judgeLocally(submissionId, language, code, testcases, problem);
    }

    private Map<String, Object> runInDocker(String language, String code, String input,
                                             String expectedOutput, int timeLimitMs) {
        ZhixueProblemTestcase testcase = ZhixueProblemTestcase.builder()
                .inputFilePath(input)
                .outputFilePath(expectedOutput != null ? expectedOutput : "")
                .timeLimitMs(timeLimitMs)
                .build();

        ZhixueProblem problem = ZhixueProblem.builder()
                .timeLimitMs(timeLimitMs)
                .memoryLimitMb(256)
                .build();

        List<ZhixueProblemTestcase> testcases = List.of(testcase);
        JudgeResult judgeResult = judgeEngine.judge(0L, language, code, testcases, problem);

        if (judgeResult == null) return null;

        Map<String, Object> sampleResult = new java.util.HashMap<>();
        sampleResult.put("input", input);
        sampleResult.put("expectedOutput", expectedOutput != null ? expectedOutput : "");
        sampleResult.put("actualOutput",
                judgeResult.getTestcaseResults().isEmpty() ? "" :
                        judgeResult.getTestcaseResults().get(0).getActualOutput());
        sampleResult.put("executionTimeMs",
                judgeResult.getTestcaseResults().isEmpty() ? 0 :
                        judgeResult.getTestcaseResults().get(0).getExecutionTimeMs());
        sampleResult.put("memoryUsedKb",
                judgeResult.getTestcaseResults().isEmpty() ? 0 :
                        judgeResult.getTestcaseResults().get(0).getMemoryUsedKb());

        String status = convertStatus(judgeResult.getStatus());
        sampleResult.put("status", status);

        if (judgeResult.getErrorMessage() != null) {
            sampleResult.put("errorMessage", judgeResult.getErrorMessage());
        }

        return sampleResult;
    }

    private Map<String, Object> runLocally(String language, String code, String input,
                                            String expectedOutput, int timeLimitMs) {
        Map<String, Object> sampleResult = new java.util.HashMap<>();
        sampleResult.put("input", input);
        sampleResult.put("expectedOutput", expectedOutput != null ? expectedOutput : "");

        LocalCodeRunner.RunResult runResult = localCodeRunner.run(language, code, input, timeLimitMs);

        sampleResult.put("actualOutput", runResult.getStdout());
        sampleResult.put("executionTimeMs", runResult.getExecutionTimeMs());
        sampleResult.put("memoryUsedKb", runResult.getMemoryUsedKb());

        if (!runResult.isSuccess()) {
            sampleResult.put("status", runResult.getStatus());
            if (runResult.getErrorMessage() != null) {
                sampleResult.put("errorMessage", runResult.getErrorMessage());
            }
            if (runResult.getStderr() != null && !runResult.getStderr().isEmpty()) {
                sampleResult.put("errorMessage",
                        (runResult.getErrorMessage() != null ? runResult.getErrorMessage() + "\n" : "") + runResult.getStderr());
            }
        } else if (expectedOutput != null && !expectedOutput.isEmpty()) {
            boolean passed = compareOutput(expectedOutput, runResult.getStdout());
            sampleResult.put("status", passed ? "accepted" : "wrong_answer");
        } else {
            sampleResult.put("status", "accepted");
        }

        return sampleResult;
    }

    private Map<String, Object> runSamplesInDocker(String language, String code,
                                                    List<ZhixueProblemSample> samples,
                                                    int timeLimitMs) {
        List<ZhixueProblemTestcase> testcases = new ArrayList<>();
        for (ZhixueProblemSample sample : samples) {
            testcases.add(ZhixueProblemTestcase.builder()
                    .id(sample.getId())
                    .inputFilePath(sample.getSampleInput())
                    .outputFilePath(sample.getSampleOutput())
                    .timeLimitMs(timeLimitMs)
                    .build());
        }

        ZhixueProblem problem = ZhixueProblem.builder()
                .timeLimitMs(timeLimitMs)
                .memoryLimitMb(256)
                .build();

        JudgeResult judgeResult = judgeEngine.judge(0L, language, code, testcases, problem);
        if (judgeResult == null) return null;

        List<Map<String, Object>> results = new ArrayList<>();
        int passedCount = 0;

        for (int i = 0; i < judgeResult.getTestcaseResults().size() && i < samples.size(); i++) {
            TestcaseResult tr = judgeResult.getTestcaseResults().get(i);
            ZhixueProblemSample sample = samples.get(i);

            Map<String, Object> sampleResult = new java.util.HashMap<>();
            sampleResult.put("sampleId", sample.getId());
            sampleResult.put("input", sample.getSampleInput());
            sampleResult.put("expectedOutput", sample.getSampleOutput());
            sampleResult.put("actualOutput", tr.getActualOutput());
            sampleResult.put("executionTimeMs", tr.getExecutionTimeMs());
            sampleResult.put("memoryUsedKb", tr.getMemoryUsedKb());

            String status = convertStatus(tr.getStatus());
            sampleResult.put("status", status);

            if (tr.getErrorOutput() != null && !tr.getErrorOutput().isEmpty()) {
                sampleResult.put("errorMessage", tr.getErrorOutput());
            }

            if ("accepted".equals(status)) passedCount++;
            results.add(sampleResult);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "completed");
        result.put("results", results);
        result.put("passedCount", passedCount);
        result.put("totalCount", samples.size());
        return result;
    }

    private Map<String, Object> runSamplesLocally(String language, String code,
                                                   List<ZhixueProblemSample> samples,
                                                   int timeLimitMs) {
        List<Map<String, Object>> results = new ArrayList<>();
        int passedCount = 0;

        for (ZhixueProblemSample sample : samples) {
            Map<String, Object> sampleResult = new java.util.HashMap<>();
            sampleResult.put("sampleId", sample.getId());
            sampleResult.put("input", sample.getSampleInput());
            sampleResult.put("expectedOutput", sample.getSampleOutput());

            LocalCodeRunner.RunResult runResult = localCodeRunner.run(language, code, sample.getSampleInput(), timeLimitMs);

            sampleResult.put("actualOutput", runResult.getStdout());
            sampleResult.put("executionTimeMs", runResult.getExecutionTimeMs());
            sampleResult.put("memoryUsedKb", runResult.getMemoryUsedKb());

            if (!runResult.isSuccess()) {
                sampleResult.put("status", runResult.getStatus());
                if (runResult.getErrorMessage() != null) {
                    sampleResult.put("errorMessage", runResult.getErrorMessage());
                }
                if (runResult.getStderr() != null && !runResult.getStderr().isEmpty()) {
                    sampleResult.put("errorMessage",
                            (runResult.getErrorMessage() != null ? runResult.getErrorMessage() + "\n" : "") + runResult.getStderr());
                }
            } else {
                boolean passed = compareOutput(sample.getSampleOutput(), runResult.getStdout());
                sampleResult.put("status", passed ? "accepted" : "wrong_answer");
                if (passed) passedCount++;
            }

            results.add(sampleResult);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "completed");
        result.put("results", results);
        result.put("passedCount", passedCount);
        result.put("totalCount", samples.size());
        return result;
    }

    private JudgeResult judgeLocally(Long submissionId, String language, String code,
                                      List<ZhixueProblemTestcase> testcases, ZhixueProblem problem) {
        int timeLimitMs = problem.getTimeLimitMs() != null ? problem.getTimeLimitMs() : 2000;
        int memoryLimitMb = problem.getMemoryLimitMb() != null ? problem.getMemoryLimitMb() : 256;

        LocalCodeRunner.RunResult compileCheck = localCodeRunner.run(language, code, "", timeLimitMs);
        if (!compileCheck.isSuccess() && "compilation_error".equals(compileCheck.getStatus())) {
            return JudgeResult.builder()
                    .status("CE")
                    .score(0.0)
                    .totalCount(testcases.size())
                    .passedCount(0)
                    .errorMessage(compileCheck.getErrorMessage())
                    .testcaseResults(new ArrayList<>())
                    .build();
        }

        int passedCount = 0;
        int totalTimeMs = 0;
        boolean hasTLE = false;
        boolean hasMLE = false;
        boolean hasRE = false;
        boolean hasWA = false;
        List<TestcaseResult> testcaseResults = new ArrayList<>();

        for (int i = 0; i < testcases.size(); i++) {
            ZhixueProblemTestcase testcase = testcases.get(i);
            int tcTimeLimit = testcase.getTimeLimitMs() != null ? testcase.getTimeLimitMs() : timeLimitMs;

            LocalCodeRunner.RunResult runResult = localCodeRunner.run(language, code,
                    testcase.getInputFilePath(), tcTimeLimit);

            TestcaseResult.TestcaseResultBuilder resultBuilder = TestcaseResult.builder()
                    .testcaseId(testcase.getId())
                    .testcaseName(testcase.getTestcaseName())
                    .executionTimeMs((int) runResult.getExecutionTimeMs())
                    .memoryUsedKb(runResult.getMemoryUsedKb())
                    .actualOutput(runResult.getStdout())
                    .errorOutput(runResult.getStderr());

            if (!runResult.isSuccess()) {
                String status;
                if ("time_limit_exceeded".equals(runResult.getStatus())) {
                    status = "TLE";
                    hasTLE = true;
                } else if ("memory_limit_exceeded".equals(runResult.getStatus())) {
                    status = "MLE";
                    hasMLE = true;
                } else {
                    status = "RE";
                    hasRE = true;
                }
                resultBuilder.status(status);
                testcaseResults.add(resultBuilder.build());

                log.info("Local judge submission {} testcase {} [{}]: {} (time={}ms)",
                        submissionId, i + 1, testcase.getTestcaseName(), status,
                        runResult.getExecutionTimeMs());
            } else {
                boolean accepted = compareOutput(testcase.getOutputFilePath(), runResult.getStdout());
                resultBuilder.status(accepted ? "AC" : "WA");
                testcaseResults.add(resultBuilder.build());

                if (accepted) {
                    passedCount++;
                    log.info("Local judge submission {} testcase {} [{}]: AC (time={}ms)",
                            submissionId, i + 1, testcase.getTestcaseName(),
                            runResult.getExecutionTimeMs());
                } else {
                    hasWA = true;
                    log.info("Local judge submission {} testcase {} [{}]: WA (time={}ms)",
                            submissionId, i + 1, testcase.getTestcaseName(),
                            runResult.getExecutionTimeMs());
                }
            }

            totalTimeMs += runResult.getExecutionTimeMs();
        }

        String finalStatus;
        if (passedCount == testcases.size()) {
            finalStatus = "AC";
        } else if (hasTLE) {
            finalStatus = "TLE";
        } else if (hasMLE) {
            finalStatus = "MLE";
        } else if (hasRE) {
            finalStatus = "RE";
        } else if (hasWA) {
            finalStatus = passedCount > 0 ? "PA" : "WA";
        } else {
            finalStatus = passedCount > 0 ? "PA" : "WA";
        }
        double score = testcases.isEmpty() ? 0.0 : (passedCount * 100.0 / testcases.size());

        long maxMemoryKb = 0L;
        for (TestcaseResult tr : testcaseResults) {
            if (tr.getMemoryUsedKb() != null && tr.getMemoryUsedKb() > maxMemoryKb) {
                maxMemoryKb = tr.getMemoryUsedKb();
            }
        }

        log.info("Local judge submission {} final result: {} ({}/{} passed, score={})",
                submissionId, finalStatus, passedCount, testcases.size(), String.format("%.1f", score));

        return JudgeResult.builder()
                .status(finalStatus)
                .score(score)
                .totalTimeMs(totalTimeMs)
                .maxMemoryKb(maxMemoryKb)
                .passedCount(passedCount)
                .totalCount(testcases.size())
                .testcaseResults(testcaseResults)
                .build();
    }

    private String convertStatus(String dockerStatus) {
        if (dockerStatus == null) return "system_error";
        return switch (dockerStatus) {
            case "AC" -> "accepted";
            case "PA" -> "partial_accepted";
            case "WA" -> "wrong_answer";
            case "TLE" -> "time_limit_exceeded";
            case "MLE" -> "memory_limit_exceeded";
            case "RE" -> "runtime_error";
            case "CE" -> "compilation_error";
            case "SE" -> "system_error";
            case "SKIP" -> "skip";
            default -> dockerStatus.toLowerCase();
        };
    }

    private boolean compareOutput(String expected, String actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        return normalizeOutput(expected).equals(normalizeOutput(actual));
    }

    private String normalizeOutput(String output) {
        String[] lines = output.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].stripTrailing();
            if (i > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString().stripTrailing();
    }
}
