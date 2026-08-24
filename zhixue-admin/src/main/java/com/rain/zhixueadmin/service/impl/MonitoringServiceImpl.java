package com.rain.zhixueadmin.service.impl;

import com.rain.zhixueadmin.config.SessionListener;
import com.rain.zhixueadmin.service.MonitoringService;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MonitoringServiceImpl implements MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringServiceImpl.class);

    private static final DecimalFormat DF_1 = new DecimalFormat("#.0");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneId.of("UTC"));

    @Autowired(required = false)
    @Qualifier("adminDataSource")
    private DataSource adminDataSource;

    @Autowired(required = false)
    @Qualifier("userDataSource")
    private DataSource userDataSource;

    @Autowired(required = false)
    @Qualifier("problemDataSource")
    private DataSource problemDataSource;

    @Autowired(required = false)
    @Qualifier("learningDataSource")
    private DataSource learningDataSource;

    @Autowired(required = false)
    private MetricsEndpoint metricsEndpoint;

    @Autowired(required = false)
    private SessionListener sessionListener;

    @Autowired(required = false)
    @Qualifier("adminSqlSessionTemplate")
    private org.mybatis.spring.SqlSessionTemplate adminSqlSessionTemplate;

    @Override
    public Map<String, Object> getSystemDetail() {
        Map<String, Object> result = new LinkedHashMap<>();
        Instant checkTime = Instant.now();

        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            OperatingSystem os = si.getOperatingSystem();
            CentralProcessor processor = hal.getProcessor();

            double cpuUsage = getCpuUsage(processor);
            result.put("cpuUsage", Double.parseDouble(DF_1.format(cpuUsage)));

            double memoryUsage = getMemoryUsage(hal);
            result.put("memoryUsage", Double.parseDouble(DF_1.format(memoryUsage)));

            double diskUsage = getRootDiskUsage(os);
            result.put("diskUsage", Double.parseDouble(DF_1.format(diskUsage)));

            int dbConnections = getDatabaseConnections();
            result.put("databaseConnections", dbConnections);

            result.put("onlineUsers", getOnlineUsersCount());

            double systemLoad = getSystemLoad(hal.getProcessor());
            result.put("systemLoad", Double.parseDouble(DF_1.format(systemLoad)));

            String uptime = formatUptime(os.getSystemUptime());
            result.put("uptime", uptime);

        } catch (Exception e) {
            log.error("获取系统监控数据异常", e);
            safePut(result, "cpuUsage", 0.0);
            safePut(result, "memoryUsage", 0.0);
            safePut(result, "diskUsage", 0.0);
            safePut(result, "databaseConnections", 0);
            safePut(result, "onlineUsers", 0);
            safePut(result, "systemLoad", 0.0);
            result.put("uptime", "0天0小时0分钟");
        }

        result.put("lastCheckTime", ISO_FORMATTER.format(checkTime));

        return result;
    }

    private double getCpuUsage(CentralProcessor processor) {
        try {
            double load = processor.getSystemCpuLoad(500);
            return Math.max(0.0, Math.min(100.0, load * 100));
        } catch (Exception e) {
            log.error("CPU使用率计算异常", e);
            return 0.0;
        }
    }

    private double getMemoryUsage(HardwareAbstractionLayer hal) {
        GlobalMemory memory = hal.getMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        return (total - available) * 100.0 / total;
    }

    private double getRootDiskUsage(OperatingSystem os) {
        FileSystem fileSystem = os.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();

        for (OSFileStore fs : fileStores) {
            if ("/".equals(fs.getMount()) || "C:\\".equals(fs.getMount())) {
                long total = fs.getTotalSpace();
                long used = fs.getTotalSpace() - fs.getUsableSpace();
                return total > 0 ? (used * 100.0 / total) : 0.0;
            }
        }

        if (!fileStores.isEmpty()) {
            OSFileStore fs = fileStores.get(0);
            long total = fs.getTotalSpace();
            long used = fs.getTotalSpace() - fs.getUsableSpace();
            return total > 0 ? (used * 100.0 / total) : 0.0;
        }
        return 0.0;
    }

    private int getDatabaseConnections() {
        int totalConnections = 0;

        if (metricsEndpoint != null) {
            try {
                MetricsEndpoint.MetricDescriptor descriptor = metricsEndpoint.metric("hikaricp.connections.active", null);
                if (descriptor != null && !descriptor.getMeasurements().isEmpty()) {
                    int sum = 0;
                    for (var measurement : descriptor.getMeasurements()) {
                        sum += measurement.getValue().intValue();
                    }
                    if (sum > 0) {
                        log.debug("通过Actuator获取数据库连接数: {}", sum);
                        return sum;
                    }
                }
            } catch (Exception e) {
                log.debug("通过Actuator获取连接数失败", e);
            }
        }

        totalConnections += getHikariConnections(adminDataSource, "admin");
        totalConnections += getHikariConnections(userDataSource, "user");
        totalConnections += getHikariConnections(problemDataSource, "problem");
        totalConnections += getHikariConnections(learningDataSource, "learning");

        if (totalConnections > 0) {
            log.debug("通过HikariCP反射获取数据库总连接数: {}", totalConnections);
            return totalConnections;
        }

        return queryDatabaseConnectionsDirectly();
    }

    private int getHikariConnections(DataSource ds, String name) {
        if (ds == null) return 0;
        try {
            if (ds.getClass().getName().contains("HikariDataSource")) {
                java.lang.reflect.Method method = ds.getClass().getMethod("getHikariPoolMXBean");
                Object mxBean = method.invoke(ds);
                if (mxBean != null) {
                    java.lang.reflect.Method activeMethod = mxBean.getClass().getMethod("getActiveConnections");
                    int active = (int) activeMethod.invoke(mxBean);
                    log.debug("HikariCP [{}] active connections: {}", name, active);
                    return active;
                }
            }
        } catch (Exception e) {
            log.debug("通过HikariCP反射获取[{}]连接数失败: {}", name, e.getMessage());
        }
        return 0;
    }

    private int queryDatabaseConnectionsDirectly() {
        JdbcTemplate jt = null;
        String jdbcUrl = null;

        if (adminSqlSessionTemplate != null) {
            try {
                Connection conn = adminSqlSessionTemplate.getSqlSessionFactory().getConfiguration().getEnvironment().getDataSource().getConnection();
                jdbcUrl = conn.getMetaData().getURL();
                conn.close();
            } catch (Exception e) {
                log.debug("获取admin数据源URL失败: {}", e.getMessage());
            }
        }

        if (jt == null && adminDataSource != null) {
            jt = new JdbcTemplate(adminDataSource);
        }

        if (jt != null) {
            try {
                Integer connections = jt.queryForObject(
                        "SHOW STATUS LIKE 'Threads_connected'",
                        (rs, rowNum) -> {
                            if (rs.next()) {
                                return Integer.parseInt(rs.getString("Value"));
                            }
                            return 0;
                        }
                );
                if (connections != null && connections > 0) {
                    log.debug("通过SQL查询数据库连接数: {}", connections);
                    return Math.max(0, connections - 1);
                }
            } catch (Exception e) {
                log.warn("通过JdbcTemplate查询数据库连接数失败: {}", e.getMessage());
            }
        }

        if (jdbcUrl != null) {
            String url = jdbcUrl.replaceFirst("/[^/]*\\?", "/mysql?");
            if (!url.contains("?")) {
                url += "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            } else {
                url += "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            }
            try {
                String username = null;
                String password = null;
                if (adminDataSource != null) {
                    try (Connection testConn = adminDataSource.getConnection()) {
                        // can't easily extract credentials from HikariDataSource
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log.debug("直接查询数据库连接数失败: {}", e.getMessage());
            }
        }

        log.warn("所有获取数据库连接数策略均失败，返回0");
        return 0;
    }

    private double getSystemLoad(CentralProcessor processor) {
        try {
            double[] loadAverage = processor.getSystemLoadAverage(3);
            if (loadAverage != null && loadAverage.length > 0) {
                if (loadAverage[0] >= 0) {
                    return loadAverage[0];
                }
            }
        } catch (Exception e) {
            log.warn("获取系统负载异常", e);
        }
        return 0.0;
    }

    private int getOnlineUsersCount() {
        if (sessionListener != null) {
            return sessionListener.getActiveSessionsCount();
        }
        return 0;
    }

    private String formatUptime(long uptimeSeconds) {
        Duration duration = Duration.ofSeconds(uptimeSeconds);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        return String.format("%d天%d小时%d分钟", days, hours, minutes);
    }

    private void safePut(Map<String, Object> map, String key, double value) {
        try {
            map.put(key, Double.parseDouble(DF_1.format(value)));
        } catch (Exception e) {
            map.put(key, 0.0);
        }
    }

    private void safePut(Map<String, Object> map, String key, int value) {
        map.put(key, value);
    }
}
