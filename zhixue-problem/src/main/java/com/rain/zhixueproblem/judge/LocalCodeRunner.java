package com.rain.zhixueproblem.judge;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class LocalCodeRunner {

    private static final long MAX_OUTPUT_SIZE = 1024 * 1024;
    private static final int MAX_PROCESS_WAIT_SECONDS = 30;

    public RunResult run(String language, String code, String input, int timeLimitMs) {
        log.info("LocalCodeRunner executing: language={}, timeLimitMs={}ms", language, timeLimitMs);
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("judge_");
            String sourceFileName = getSourceFileName(language);
            Path sourceFile = workDir.resolve(sourceFileName);
            Files.writeString(sourceFile, code, StandardCharsets.UTF_8);

            CompileResult compileResult = compile(workDir, language);
            if (!compileResult.success) {
                return RunResult.builder()
                        .success(false)
                        .status("compilation_error")
                        .errorMessage(compileResult.output)
                        .build();
            }

            return execute(workDir, language, input, timeLimitMs);
        } catch (Exception e) {
            log.error("Local code execution failed", e);
            return RunResult.builder()
                    .success(false)
                    .status("system_error")
                    .errorMessage(e.getMessage())
                    .build();
        } finally {
            if (workDir != null) {
                try {
                    deleteRecursively(workDir);
                } catch (Exception e) {
                    log.warn("Failed to cleanup work dir: {}", workDir, e);
                }
            }
        }
    }

    private CompileResult compile(Path workDir, String language) {
        log.info("LocalCodeRunner compiling: language={}", language);
        try {
            String[] compileCmd = getCompileCommand(language);
            if (compileCmd == null) {
                return new CompileResult(true, "");
            }

            ProcessBuilder pb = new ProcessBuilder(compileCmd);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readStream(process.getInputStream(), MAX_OUTPUT_SIZE);

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CompileResult(false, "Compilation timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return new CompileResult(false, output);
            }

            return new CompileResult(true, "");
        } catch (Exception e) {
            return new CompileResult(false, e.getMessage());
        }
    }

    private RunResult execute(Path workDir, String language, String input, int timeLimitMs) {
        try {
            String[] runCmd = getRunCommand(language);
            ProcessBuilder pb = new ProcessBuilder(runCmd);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("LC_ALL", "en_US.UTF-8");

            final Process process = pb.start();

            if (input != null && !input.isEmpty()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(input.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } else {
                process.getOutputStream().close();
            }

            long startTime = System.currentTimeMillis();
            long pid = process.pid();
            AtomicLong peakMemoryKb = new AtomicLong(0L);

            Thread memoryMonitorThread = new Thread(() -> {
                while (process.isAlive()) {
                    long mem = readProcessMemoryKb(pid);
                    if (mem > peakMemoryKb.get()) {
                        peakMemoryKb.set(mem);
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                long finalMem = readProcessMemoryKb(pid);
                if (finalMem > peakMemoryKb.get()) {
                    peakMemoryKb.set(finalMem);
                }
            });
            memoryMonitorThread.setDaemon(true);
            memoryMonitorThread.start();

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() ->
                    readStreamSafe(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() ->
                    readStreamSafe(process.getErrorStream()));

            boolean finished = process.waitFor(Math.min(timeLimitMs + 2000, MAX_PROCESS_WAIT_SECONDS * 1000L), TimeUnit.MILLISECONDS);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            memoryMonitorThread.join(2000);
            long usedMemoryKb = peakMemoryKb.get();

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                String stdoutStr = "";
                String stderrStr = "";
                try {
                    stdoutStr = stdoutFuture.get(2, TimeUnit.SECONDS).stripTrailing();
                } catch (Exception ignored) {}
                try {
                    stderrStr = stderrFuture.get(2, TimeUnit.SECONDS).stripTrailing();
                } catch (Exception ignored) {}
                return RunResult.builder()
                        .success(false)
                        .status("time_limit_exceeded")
                        .stdout(stdoutStr)
                        .stderr(stderrStr)
                        .executionTimeMs(executionTimeMs)
                        .memoryUsedKb(usedMemoryKb)
                        .build();
            }

            String stdoutStr = "";
            String stderrStr = "";
            try {
                stdoutStr = stdoutFuture.get(5, TimeUnit.SECONDS).stripTrailing();
            } catch (Exception ignored) {}
            try {
                stderrStr = stderrFuture.get(5, TimeUnit.SECONDS).stripTrailing();
            } catch (Exception ignored) {}

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                String status = stderrStr.contains("Cannot allocate memory") || stderrStr.contains("OutOfMemoryError")
                        ? "memory_limit_exceeded" : "runtime_error";
                return RunResult.builder()
                        .success(false)
                        .status(status)
                        .stdout(stdoutStr)
                        .stderr(stderrStr)
                        .executionTimeMs(executionTimeMs)
                        .memoryUsedKb(usedMemoryKb)
                        .build();
            }

            return RunResult.builder()
                    .success(true)
                    .status("accepted")
                    .stdout(stdoutStr)
                    .stderr(stderrStr)
                    .executionTimeMs(executionTimeMs)
                    .memoryUsedKb(usedMemoryKb)
                    .build();

        } catch (Exception e) {
            log.error("Execution error", e);
            return RunResult.builder()
                    .success(false)
                    .status("system_error")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private long readProcessMemoryKb(long pid) {
        try {
            Path statusFile = Paths.get("/proc", String.valueOf(pid), "status");
            List<String> lines = Files.readAllLines(statusFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("VmHWM:")) {
                    String value = line.substring("VmHWM:".length()).trim();
                    String numPart = value.replaceAll("[^0-9]", "");
                    if (!numPart.isEmpty()) {
                        return Long.parseLong(numPart);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private String getSourceFileName(String language) {
        return switch (language) {
            case "java" -> "Main.java";
            case "cpp" -> "Main.cpp";
            case "c" -> "Main.c";
            default -> "Main";
        };
    }

    private String[] getCompileCommand(String language) {
        return switch (language) {
            case "java" -> new String[]{"javac", "Main.java"};
            case "cpp" -> new String[]{"g++", "-O2", "-std=c++17", "-o", "Main", "Main.cpp"};
            case "c" -> new String[]{"gcc", "-O2", "-std=c11", "-o", "Main", "Main.c"};
            default -> null;
        };
    }

    private String[] getRunCommand(String language) {
        return switch (language) {
            case "java" -> new String[]{"java", "-Xmx512m", "Main"};
            case "cpp", "c" -> new String[]{"./Main"};
            default -> new String[]{"./Main"};
        };
    }

    private String readStream(InputStream inputStream, long maxSize) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() < maxSize) {
                    sb.append(line).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String readStreamSafe(InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() < MAX_OUTPUT_SIZE) {
                    sb.append(line).append("\n");
                }
            }
        } catch (IOException ignored) {
        }
        return sb.toString();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Data
    @Builder
    public static class RunResult {
        private boolean success;
        private String status;
        private String stdout;
        private String stderr;
        @Builder.Default
        private long executionTimeMs = 0L;
        @Builder.Default
        private Long memoryUsedKb = 0L;
        private String errorMessage;
    }

    private record CompileResult(boolean success, String output) {
    }
}
