package com.rain.zhixueproblem.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import com.rain.zhixueproblem.judge.model.Container;
import com.rain.zhixueproblem.judge.model.JudgeResult;
import com.rain.zhixueproblem.judge.model.TestcaseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JudgeEngine {

    @Autowired(required = false)
    private ContainerPoolManager containerPoolManager;

    @Autowired(required = false)
    private DockerClient dockerClient;

    private static final String WORKSPACE_PATH = "/home/judge/workspace";
    private static final String SOURCE_FILE_NAME = "Main";
    private static final String INPUT_FILE_NAME = "input.txt";

    public boolean isAvailable() {
        return containerPoolManager != null && dockerClient != null && containerPoolManager.isAvailable();
    }

    public JudgeResult judge(Long submissionId, String language, String code,
                             List<ZhixueProblemTestcase> testcases, ZhixueProblem problem) {
        if (!isAvailable()) {
            throw new RuntimeException("Docker judge engine is not available");
        }

        Container container = null;
        try {
            container = containerPoolManager.borrowContainer(language,
                    containerPoolManager.getPool(language).getBorrowTimeoutSeconds());

            writeCodeToContainer(container, language, code);

            CompileResult compileResult = compileInContainer(container, language);
            if (!compileResult.success) {
                return JudgeResult.builder()
                        .status("CE")
                        .score(0.0)
                        .totalCount(testcases.size())
                        .passedCount(0)
                        .errorMessage(compileResult.output)
                        .testcaseResults(new ArrayList<>())
                        .build();
            }

            int passedCount = 0;
            int totalTimeMs = 0;
            long maxMemoryKb = 0;
            boolean hasTLE = false;
            boolean hasMLE = false;
            boolean hasRE = false;
            boolean hasWA = false;
            List<TestcaseResult> testcaseResults = new ArrayList<>();

            for (int i = 0; i < testcases.size(); i++) {
                ZhixueProblemTestcase testcase = testcases.get(i);
                int timeLimitMs = testcase.getTimeLimitMs() != null ? testcase.getTimeLimitMs() : problem.getTimeLimitMs();
                int memoryLimitMb = testcase.getMemoryLimitMb() != null ? testcase.getMemoryLimitMb() : problem.getMemoryLimitMb();

                RunResult runResult = runInContainer(container, language, testcase, timeLimitMs, memoryLimitMb);

                TestcaseResult.TestcaseResultBuilder resultBuilder = TestcaseResult.builder()
                        .testcaseId(testcase.getId())
                        .testcaseName(testcase.getTestcaseName())
                        .executionTimeMs(runResult.executionTimeMs)
                        .memoryUsedKb(runResult.memoryUsedKb)
                        .errorOutput(runResult.stderr);

                if (!runResult.success) {
                    String status = runResult.timeout ? "TLE" : (runResult.memoryExceeded ? "MLE" : "RE");
                    resultBuilder.status(status);
                    resultBuilder.actualOutput(runResult.stdout);
                    testcaseResults.add(resultBuilder.build());

                    if ("TLE".equals(status)) hasTLE = true;
                    else if ("MLE".equals(status)) hasMLE = true;
                    else hasRE = true;

                    log.info("Submission {} testcase {} [{}]: {} (time={}ms, mem={}kb{})",
                            submissionId, i + 1, testcase.getTestcaseName(), status,
                            runResult.executionTimeMs, runResult.memoryUsedKb,
                            runResult.stderr != null && !runResult.stderr.isEmpty() ? ", err=" + runResult.stderr.substring(0, Math.min(runResult.stderr.length(), 200)) : "");
                } else {
                    boolean accepted = compareOutput(testcase.getOutputFilePath(), runResult.stdout);
                    resultBuilder.status(accepted ? "AC" : "WA");
                    resultBuilder.actualOutput(runResult.stdout);
                    testcaseResults.add(resultBuilder.build());

                    if (accepted) {
                        passedCount++;
                        log.info("Submission {} testcase {} [{}]: AC (time={}ms, mem={}kb)",
                                submissionId, i + 1, testcase.getTestcaseName(),
                                runResult.executionTimeMs, runResult.memoryUsedKb);
                    } else {
                        hasWA = true;
                        log.info("Submission {} testcase {} [{}]: WA (time={}ms, mem={}kb)",
                                submissionId, i + 1, testcase.getTestcaseName(),
                                runResult.executionTimeMs, runResult.memoryUsedKb);
                    }
                }

                totalTimeMs += runResult.executionTimeMs;
                maxMemoryKb = Math.max(maxMemoryKb, runResult.memoryUsedKb);
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

            log.info("Submission {} final result: {} ({}/{} passed, score={}, time={}ms, mem={}kb)",
                    submissionId, finalStatus, passedCount, testcases.size(),
                    String.format("%.1f", score), totalTimeMs, maxMemoryKb);

            return JudgeResult.builder()
                    .status(finalStatus)
                    .score(score)
                    .totalTimeMs(totalTimeMs)
                    .maxMemoryKb(maxMemoryKb)
                    .passedCount(passedCount)
                    .totalCount(testcases.size())
                    .testcaseResults(testcaseResults)
                    .build();

        } catch (Exception e) {
            log.error("Judge error for submission {}", submissionId, e);
            return JudgeResult.builder()
                    .status("SE")
                    .score(0.0)
                    .totalCount(testcases.size())
                    .passedCount(0)
                    .errorMessage(e.getMessage())
                    .testcaseResults(new ArrayList<>())
                    .build();
        } finally {
            if (container != null) {
                containerPoolManager.returnContainer(container);
            }
        }
    }

    private void writeCodeToContainer(Container container, String language, String code) throws Exception {
        String fileName = getSourceFileName(language);
        try {
            dockerClient.copyArchiveToContainerCmd(container.getContainerId())
                    .withTarInputStream(new ByteArrayInputStream(createTarEntry(fileName, code)))
                    .withRemotePath(WORKSPACE_PATH)
                    .exec();
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.warn("Workspace path not found in container {}, recreating it", container.getContainerId());
            ensureWorkspaceExists(container);
            dockerClient.copyArchiveToContainerCmd(container.getContainerId())
                    .withTarInputStream(new ByteArrayInputStream(createTarEntry(fileName, code)))
                    .withRemotePath(WORKSPACE_PATH)
                    .exec();
        }
    }

    private void ensureWorkspaceExists(Container container) {
        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(container.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("sh", "-c", "mkdir -p " + WORKSPACE_PATH + " && chown judge:judge " + WORKSPACE_PATH)
                    .exec();
            dockerClient.execStartCmd(execCreate.getId()).exec(
                    new com.github.dockerjava.api.async.ResultCallback.Adapter<>()).awaitCompletion(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.error("Failed to recreate workspace in container {}", container.getContainerId(), ex);
            throw new RuntimeException("Failed to recreate workspace directory", ex);
        }
    }

    private byte[] createTarEntry(String fileName, String content) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream taos =
                    new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(baos);
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry =
                    new org.apache.commons.compress.archivers.tar.TarArchiveEntry(fileName);
            entry.setSize(contentBytes.length);
            taos.putArchiveEntry(entry);
            taos.write(contentBytes);
            taos.closeArchiveEntry();
            taos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tar entry", e);
        }
    }

    private String getSourceFileName(String language) {
        return switch (language) {
            case "java" -> SOURCE_FILE_NAME + ".java";
            case "cpp" -> SOURCE_FILE_NAME + ".cpp";
            case "c" -> SOURCE_FILE_NAME + ".c";
            default -> SOURCE_FILE_NAME;
        };
    }

    private CompileResult compileInContainer(Container container, String language) {
        try {
            String compileCmd = getCompileCommand(language);
            if (compileCmd == null) {
                return new CompileResult(true, "");
            }

            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(container.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withWorkingDir(WORKSPACE_PATH)
                    .withCmd("sh", "-c", compileCmd)
                    .exec();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            var callback = new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                @Override
                public void onNext(com.github.dockerjava.api.model.Frame frame) {
                    String str = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    if (frame.getStreamType() == com.github.dockerjava.api.model.StreamType.STDOUT) {
                        stdout.append(str);
                    } else {
                        stderr.append(str);
                    }
                }
            };

            dockerClient.execStartCmd(execCreate.getId()).exec(callback).awaitCompletion(30, TimeUnit.SECONDS);

            var inspectResponse = dockerClient.inspectExecCmd(execCreate.getId()).exec();
            long exitCode = inspectResponse.getExitCodeLong() != null ? inspectResponse.getExitCodeLong() : -1;

            if (exitCode != 0) {
                return new CompileResult(false, stderr.toString());
            }

            return new CompileResult(true, stdout.toString());
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.warn("Workspace not found during compile in container {}, recreating and retrying", container.getContainerId());
            try {
                ensureWorkspaceExists(container);
                writeCodeToContainer(container, language, "");
            } catch (Exception ex) {
                return new CompileResult(false, "Workspace recovery failed: " + ex.getMessage());
            }
            return new CompileResult(false, "Workspace directory was missing, please retry");
        } catch (Exception e) {
            log.error("Compile error in container {}", container.getContainerId(), e);
            return new CompileResult(false, e.getMessage());
        }
    }

    private String getCompileCommand(String language) {
        return switch (language) {
            case "java" -> "javac " + SOURCE_FILE_NAME + ".java";
            case "cpp" -> "g++ -O2 -std=c++17 -o " + SOURCE_FILE_NAME + " " + SOURCE_FILE_NAME + ".cpp";
            case "c" -> "gcc -O2 -std=c11 -o " + SOURCE_FILE_NAME + " " + SOURCE_FILE_NAME + ".c";
            default -> null;
        };
    }

    private RunResult runInContainer(Container container, String language,
                                     ZhixueProblemTestcase testcase,
                                     int timeLimitMs, int memoryLimitMb) {
        try {
            String runCmd = getRunCommand(language);
            String input = testcase.getInputFilePath();

            String fullCmd = "unset JAVA_TOOL_OPTIONS 2>/dev/null; cat " + INPUT_FILE_NAME + " | " +
                    String.format("timeout %.1fs", timeLimitMs / 1000.0 + 0.5) + " " + runCmd;

            writeInputToContainer(container, input);

            containerPoolManager.resetContainerMemoryStats(container.getContainerId());

            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(container.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withWorkingDir(WORKSPACE_PATH)
                    .withCmd("sh", "-c", fullCmd)
                    .exec();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            long startTime = System.currentTimeMillis();

            var callback = new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                @Override
                public void onNext(com.github.dockerjava.api.model.Frame frame) {
                    String str = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    if (frame.getStreamType() == com.github.dockerjava.api.model.StreamType.STDOUT) {
                        stdout.append(str);
                    } else {
                        stderr.append(str);
                    }
                }
            };

            dockerClient.execStartCmd(execCreate.getId()).exec(callback)
                    .awaitCompletion(timeLimitMs + 2000L, TimeUnit.MILLISECONDS);

            long executionTimeMs = System.currentTimeMillis() - startTime;

            var inspectResponse = dockerClient.inspectExecCmd(execCreate.getId()).exec();
            long exitCode = inspectResponse.getExitCodeLong() != null ? inspectResponse.getExitCodeLong() : -1;

            boolean timeout = exitCode == 124;
            boolean memoryExceeded = stderr.toString().contains("Cannot allocate memory");

            long memoryKb = containerPoolManager.getContainerMemoryUsageKb(container.getContainerId());
            if (memoryKb > memoryLimitMb * 1024L) {
                memoryExceeded = true;
            }

            return RunResult.builder()
                    .success(exitCode == 0)
                    .stdout(stdout.toString().replaceAll("(?m)^Picked up \\w+.*\\n?", ""))
                    .stderr(stderr.toString())
                    .executionTimeMs((int) Math.min(executionTimeMs, timeLimitMs))
                    .memoryUsedKb(memoryKb)
                    .timeout(timeout)
                    .memoryExceeded(memoryExceeded)
                    .build();
        } catch (Exception e) {
            log.error("Run error in container {} for testcase {}", container.getContainerId(), testcase.getId(), e);
            return RunResult.builder()
                    .success(false)
                    .stdout("")
                    .stderr(e.getMessage())
                    .executionTimeMs(0)
                    .memoryUsedKb(0L)
                    .timeout(false)
                    .memoryExceeded(false)
                    .build();
        }
    }

    private void writeInputToContainer(Container container, String input) throws Exception {
        try {
            dockerClient.copyArchiveToContainerCmd(container.getContainerId())
                    .withTarInputStream(new ByteArrayInputStream(createTarEntry(INPUT_FILE_NAME, input)))
                    .withRemotePath(WORKSPACE_PATH)
                    .exec();
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.warn("Workspace path not found in container {} when writing input, recreating it", container.getContainerId());
            ensureWorkspaceExists(container);
            dockerClient.copyArchiveToContainerCmd(container.getContainerId())
                    .withTarInputStream(new ByteArrayInputStream(createTarEntry(INPUT_FILE_NAME, input)))
                    .withRemotePath(WORKSPACE_PATH)
                    .exec();
        }
    }

    private String getRunCommand(String language) {
        return switch (language) {
            case "java" -> "java -Xmx512m " + SOURCE_FILE_NAME;
            case "cpp", "c" -> "./" + SOURCE_FILE_NAME;
            default -> "./" + SOURCE_FILE_NAME;
        };
    }

    public boolean compareOutput(String expected, String actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;

        String normalizedExpected = normalizeOutput(expected);
        String normalizedActual = normalizeOutput(actual);

        return normalizedExpected.equals(normalizedActual);
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

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class CompileResult {
        private boolean success;
        private String output;
    }

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class RunResult {
        private boolean success;
        private String stdout;
        private String stderr;
        private int executionTimeMs;
        private long memoryUsedKb;
        private boolean timeout;
        private boolean memoryExceeded;
    }
}
