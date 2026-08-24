package com.rain.zhixueadmin.service.impl;

import com.rain.zhixueadmin.mapper.stats.learning.LearningStatsMapper;
import com.rain.zhixueadmin.mapper.stats.problem.ProblemStatsMapper;
import com.rain.zhixueadmin.mapper.stats.user.UserStatsMapper;
import com.rain.zhixueadmin.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final UserStatsMapper userStatsMapper;
    private final ProblemStatsMapper problemStatsMapper;
    private final LearningStatsMapper learningStatsMapper;

    @Override
    public Map<String, Object> getUserStats() {
        Map<String, Object> users = new LinkedHashMap<>();
        try {
            Long totalUsers = userStatsMapper.countTotalUsers();
            Long activeUsers = userStatsMapper.countActiveUsers(7);
            Long newUsersToday = userStatsMapper.countNewUsersToday();
            Double growthRate = userStatsMapper.calculateGrowthRate(7);
            
            users.put("total", totalUsers != null ? totalUsers : 0L);
            users.put("active", activeUsers != null ? activeUsers : 0L);
            users.put("newToday", newUsersToday != null ? newUsersToday : 0L);
            users.put("growth", growthRate != null ? growthRate : 0.0);
        } catch (Exception e) {
            log.error("获取用户统计失败: {}", e.getMessage());
            users.put("total", 0L);
            users.put("active", 0L);
            users.put("newToday", 0L);
            users.put("growth", 0.0);
        }
        return users;
    }

    @Override
    public Map<String, Object> getProblemStats() {
        Map<String, Object> problems = new LinkedHashMap<>();
        try {
            Long totalProblems = problemStatsMapper.countTotalProblems();
            Long easyCount = problemStatsMapper.countProblemsByDifficulty("easy");
            Long mediumCount = problemStatsMapper.countProblemsByDifficulty("medium");
            Long hardCount = problemStatsMapper.countProblemsByDifficulty("hard");
            Long totalSubmissions = problemStatsMapper.countTotalSubmissions();
            
            problems.put("total", totalProblems != null ? totalProblems : 0L);
            problems.put("easy", easyCount != null ? easyCount : 0L);
            problems.put("medium", mediumCount != null ? mediumCount : 0L);
            problems.put("hard", hardCount != null ? hardCount : 0L);
            problems.put("submissions", totalSubmissions != null ? totalSubmissions : 0L);
        } catch (Exception e) {
            log.error("获取题目统计失败: {}", e.getMessage());
            problems.put("total", 0L);
            problems.put("easy", 0L);
            problems.put("medium", 0L);
            problems.put("hard", 0L);
            problems.put("submissions", 0L);
        }
        return problems;
    }

    @Override
    public Map<String, Object> getLearningStats() {
        Map<String, Object> learning = new LinkedHashMap<>();
        try {
            Long activePaths = learningStatsMapper.countActiveLearningPaths();
            Long completedPaths = learningStatsMapper.countCompletedLearningPaths();
            Long totalSteps = learningStatsMapper.countTotalLearningSteps();
            Double avgProgress = learningStatsMapper.calculateAverageProgress();
            
            learning.put("activePaths", activePaths != null ? activePaths : 0L);
            learning.put("completedPaths", completedPaths != null ? completedPaths : 0L);
            learning.put("totalSteps", totalSteps != null ? totalSteps : 0L);
            learning.put("averageProgress", avgProgress != null ? Math.round(avgProgress * 10.0) / 10.0 : 0.0);
        } catch (Exception e) {
            log.error("获取学习统计失败: {}", e.getMessage());
            learning.put("activePaths", 0L);
            learning.put("completedPaths", 0L);
            learning.put("totalSteps", 0L);
            learning.put("averageProgress", 0.0);
        }
        return learning;
    }

    @Override
    public Map<String, Object> getSystemStats() {
        Map<String, Object> system = new LinkedHashMap<>();
        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            long uptimeMillis = runtimeBean.getUptime();
            Duration uptime = Duration.ofMillis(uptimeMillis);
            long days = uptime.toDays();
            long hours = uptime.toHoursPart();
            long minutes = uptime.toMinutesPart();
            String uptimeStr = String.format("%d days, %dh %dm", days, hours, minutes);
            
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            OperatingSystem os = systemInfo.getOperatingSystem();
            
            double cpuLoad = hardware.getProcessor().getSystemLoadAverage(1)[0];
            if (cpuLoad < 0) {
                cpuLoad = 0;
            }
            
            long totalMemory = hardware.getMemory().getTotal();
            long availableMemory = hardware.getMemory().getAvailable();
            long usedMemory = totalMemory - availableMemory;
            double memoryUsage = (double) usedMemory / totalMemory * 100;
            
            Runtime runtime = Runtime.getRuntime();
            long jvmMaxMemory = runtime.maxMemory();
            long jvmTotalMemory = runtime.totalMemory();
            long jvmFreeMemory = runtime.freeMemory();
            long jvmUsedMemory = jvmTotalMemory - jvmFreeMemory;
            
            int availableProcessors = runtime.availableProcessors();
            
            OSProcess process = os.getProcess(os.getProcessId());
            double processCpuLoad = process.getProcessCpuLoadBetweenTicks(process);
            
            system.put("uptime", uptimeStr);
            system.put("cpuUsage", Math.round(cpuLoad * 100.0) / 100.0);
            system.put("memoryUsage", Math.round(memoryUsage * 10.0) / 10.0);
            system.put("jvmMaxMemory", formatBytes(jvmMaxMemory));
            system.put("jvmUsedMemory", formatBytes(jvmUsedMemory));
            system.put("availableProcessors", availableProcessors);
            system.put("processCpuLoad", Math.round(processCpuLoad * 100.0) / 100.0);
            
        } catch (Exception e) {
            log.error("获取系统统计失败: {}", e.getMessage());
            system.put("uptime", "N/A");
            system.put("cpuUsage", 0.0);
            system.put("memoryUsage", 0.0);
        }
        return system;
    }

    @Override
    public Map<String, Object> getAllStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users", getUserStats());
        stats.put("problems", getProblemStats());
        stats.put("learning", getLearningStats());
        stats.put("system", getSystemStats());
        stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return stats;
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}
