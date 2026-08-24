package com.rain.zhixueproblem.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import com.rain.zhixueproblem.judge.config.ContainerPoolConfig;
import com.rain.zhixueproblem.judge.config.HealthCheckConfig;
import com.rain.zhixueproblem.judge.config.JudgeMachineConfig;
import com.rain.zhixueproblem.judge.model.Container;
import com.rain.zhixueproblem.judge.model.ContainerStatus;
import com.rain.zhixueproblem.judge.model.LanguageContainerPool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class ContainerPoolManager {

    @Autowired(required = false)
    private DockerClient dockerClient;

    @Autowired(required = false)
    private ContainerPoolConfig containerPoolConfig;

    @Autowired(required = false)
    private JudgeMachineConfig judgeMachineConfig;

    @Autowired(required = false)
    private HealthCheckConfig healthCheckConfig;

    @Value("${judge.docker.host:unix:///var/run/docker.sock}")
    private String configuredDockerHost;

    private final Map<String, LanguageContainerPool> poolMap = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean dockerConnected = new AtomicBoolean(false);

    private final AtomicInteger totalBorrowCount = new AtomicInteger(0);
    private final AtomicInteger totalReturnCount = new AtomicInteger(0);
    private volatile long lastScaleUpTime = 0;
    private volatile int consecutiveLowUtilizationChecks = 0;

    private static final int LOW_UTILIZATION_THRESHOLD_PERCENT = 30;
    private static final int LOW_UTILIZATION_CONSECUTIVE_CHECKS = 3;
    private static final long SCALE_UP_COOLDOWN_MS = 120_000;

    @PostConstruct
    public void init() {
        log.info("ContainerPoolManager init: dockerClient={}, containerPoolConfig={}, judgeMachineConfig={}, healthCheckConfig={}",
                dockerClient != null, containerPoolConfig != null, judgeMachineConfig != null, healthCheckConfig != null);
        if (!checkDockerConnection()) {
            log.warn("Docker is not available, container pool will not be initialized. LocalCodeRunner will be used as fallback.");
            return;
        }
        initializePools();
    }

    private boolean reconnectDockerClient() {
        log.info("Attempting Docker client reconnection...");
        try {
            String dockerHost = System.getenv("DOCKER_HOST");
            if (dockerHost == null || dockerHost.isEmpty()) {
                dockerHost = configuredDockerHost;
            }
            log.info("Reconnecting Docker client with host: {}", dockerHost);

            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();
            DockerClient newClient = DockerClientImpl.getInstance(config)
                    .withDockerCmdExecFactory(new NettyDockerCmdExecFactory()
                            .withConnectTimeout(30000));
            newClient.pingCmd().exec();
            this.dockerClient = newClient;
            log.info("Docker client reconnected successfully to {}", config.getDockerHost());
            return true;
        } catch (Exception e) {
            log.info("Docker client reconnection failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkDockerConnection() {
        log.info("Checking Docker connection: dockerClient={}, containerPoolConfig={}, judgeMachineConfig={}, healthCheckConfig={}",
                dockerClient != null, containerPoolConfig != null, judgeMachineConfig != null, healthCheckConfig != null);
        if (dockerClient == null) {
            if (!reconnectDockerClient()) {
                return false;
            }
        }
        if (containerPoolConfig == null || judgeMachineConfig == null || healthCheckConfig == null) {
            log.warn("Docker connection skipped - missing config: containerPoolConfig={}, judgeMachineConfig={}, healthCheckConfig={}",
                    containerPoolConfig != null, judgeMachineConfig != null, healthCheckConfig != null);
            return false;
        }
        try {
            dockerClient.pingCmd().exec();
            dockerConnected.set(true);
            log.info("Docker daemon connection successful");
            return true;
        } catch (Exception e) {
            log.warn("Docker daemon connection failed: {}", e.getMessage());
            dockerConnected.set(false);
            return false;
        }
    }

    private void initializePools() {
        log.info("Initializing container pools for java, cpp, c...");
        try {
            poolMap.put("java", createPool(containerPoolConfig.getJava()));
            poolMap.put("cpp", createPool(containerPoolConfig.getCpp()));
            poolMap.put("c", createPool(containerPoolConfig.getC()));

            for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
                ensureImageExists(entry.getValue().getImage());
            }

            for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
                ensureCoreSize(entry.getValue());
            }

            initialized.set(true);
            log.info("Container pool manager initialized with pools: {}", poolMap.keySet());
        } catch (Exception e) {
            log.error("Failed to initialize container pools: {}", e.getMessage());
            initialized.set(false);
        }
    }

    private void ensureImageExists(String imageName) {
        try {
            boolean imageExists = dockerClient.listImagesCmd()
                    .withImageNameFilter(imageName)
                    .exec()
                    .stream()
                    .anyMatch(img -> img.getRepoTags() != null &&
                            java.util.Arrays.asList(img.getRepoTags()).contains(imageName));
            if (!imageExists) {
                log.info("Docker image {} not found, attempting to build...", imageName);
                buildJudgeImage(imageName);
            }
        } catch (Exception e) {
            log.warn("Failed to check/build image {}: {}", imageName, e.getMessage());
        }
    }

    private void buildJudgeImage(String imageName) {
        try {
            String dockerfileDir;
            if (imageName.startsWith("judge-java")) {
                dockerfileDir = "/app/docker/judge";
            } else {
                dockerfileDir = "/app/docker/judge";
            }
            java.io.File dockerfileDirFile = new java.io.File(dockerfileDir);
            if (!dockerfileDirFile.exists()) {
                dockerfileDir = System.getProperty("user.dir") + "/docker/judge";
                dockerfileDirFile = new java.io.File(dockerfileDir);
            }
            if (!dockerfileDirFile.exists()) {
                log.warn("Docker build directory not found, skipping image build for {}", imageName);
                return;
            }
            String dockerfile = imageName.startsWith("judge-java") ? "Dockerfile.judge-java" : "Dockerfile.judge-cpp";
            java.io.File dockerfileFile = new java.io.File(dockerfileDirFile, dockerfile);
            if (!dockerfileFile.exists()) {
                log.warn("Dockerfile {} not found in {}, skipping image build", dockerfile, dockerfileDir);
                return;
            }
            log.info("Building Docker image {} from {}...", imageName, dockerfileFile.getAbsolutePath());
            dockerClient.buildImageCmd()
                    .withDockerfile(dockerfileFile)
                    .withTags(java.util.Collections.singleton(imageName))
                    .start()
                    .awaitCompletion();
            log.info("Docker image {} built successfully", imageName);
        } catch (Exception e) {
            log.warn("Failed to build Docker image {}: {}", imageName, e.getMessage());
        }
    }

    public void ensurePoolsReady() {
        if (!dockerConnected.get()) {
            if (checkDockerConnection()) {
                if (!initialized.get()) {
                    initializePools();
                }
            } else {
                throw new RuntimeException("Docker is not available");
            }
        }
        if (!initialized.get()) {
            throw new RuntimeException("Container pool is not initialized");
        }
    }

    public boolean isAvailable() {
        return dockerConnected.get() && initialized.get();
    }

    @PreDestroy
    public void destroy() {
        for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
            LanguageContainerPool pool = entry.getValue();
            for (Container container : pool.getIdleQueue()) {
                destroyContainer(container);
            }
            for (Container container : pool.getActiveSet()) {
                destroyContainer(container);
            }
            pool.getIdleQueue().clear();
            pool.getActiveSet().clear();
        }
        initialized.set(false);
        log.info("All containers destroyed");
    }

    private LanguageContainerPool createPool(ContainerPoolConfig.PoolConfig poolConfig) {
        return new LanguageContainerPool(
                poolConfig.getCoreSize(),
                poolConfig.getMaxSize(),
                poolConfig.getMaxIdleTimeSeconds(),
                poolConfig.getBorrowTimeoutSeconds(),
                poolConfig.getMaxReuseCount(),
                poolConfig.getImage()
        );
    }

    public Container borrowContainer(String language, int timeoutSeconds) throws Exception {
        ensurePoolsReady();
        LanguageContainerPool pool = poolMap.get(language);
        if (pool == null) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }

        if (!pool.getSemaphore().tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
            throw new TimeoutException("Timeout acquiring semaphore for language: " + language);
        }

        try {
            Container container = pool.getIdleQueue().poll();
            if (container != null) {
                container.setStatus(ContainerStatus.ACTIVE);
                container.setLastActiveTime(System.currentTimeMillis());
                pool.getActiveSet().add(container);
                totalBorrowCount.incrementAndGet();
                log.debug("Borrowed idle container {} for language {}", container.getContainerId(), language);
                return container;
            }

            if (pool.getActiveSet().size() < pool.getMaxSize()) {
                container = createContainer(language);
                container.setStatus(ContainerStatus.ACTIVE);
                container.setLastActiveTime(System.currentTimeMillis());
                pool.getActiveSet().add(container);
                totalBorrowCount.incrementAndGet();
                log.debug("Created and borrowed new container {} for language {}", container.getContainerId(), language);
                return container;
            }

            container = pool.getIdleQueue().poll(timeoutSeconds, TimeUnit.SECONDS);
            if (container != null) {
                container.setStatus(ContainerStatus.ACTIVE);
                container.setLastActiveTime(System.currentTimeMillis());
                pool.getActiveSet().add(container);
                totalBorrowCount.incrementAndGet();
                log.debug("Borrowed container {} after waiting for language {}", container.getContainerId(), language);
                return container;
            }

            throw new TimeoutException("Timeout borrowing container for language: " + language);
        } catch (Exception e) {
            pool.getSemaphore().release();
            throw e;
        }
    }

    public void returnContainer(Container container) {
        LanguageContainerPool pool = poolMap.get(container.getLanguage());
        if (pool == null) {
            destroyContainer(container);
            return;
        }

        pool.getActiveSet().remove(container);
        totalReturnCount.incrementAndGet();

        try {
            resetContainer(container);
        } catch (Exception e) {
            log.error("Failed to reset container {}, destroying it", container.getContainerId(), e);
            destroyContainer(container);
            ensureCoreSize(pool);
            pool.getSemaphore().release();
            return;
        }

        container.setReuseCount(container.getReuseCount() + 1);

        if (container.getReuseCount() >= pool.getMaxReuseCount()) {
            log.debug("Container {} reached max reuse count {}, destroying", container.getContainerId(), pool.getMaxReuseCount());
            destroyContainer(container);
            ensureCoreSize(pool);
            pool.getSemaphore().release();
            return;
        }

        if (!isHealthy(container)) {
            log.warn("Container {} unhealthy after use, destroying", container.getContainerId());
            destroyContainer(container);
            ensureCoreSize(pool);
            pool.getSemaphore().release();
            return;
        }

        container.setStatus(ContainerStatus.IDLE);
        container.setLastActiveTime(System.currentTimeMillis());
        pool.getIdleQueue().offer(container);
        pool.getSemaphore().release();
        log.debug("Returned container {} to pool for language {}", container.getContainerId(), container.getLanguage());
    }

    private void resetContainer(Container container) throws Exception {
        ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(container.getContainerId())
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("sh", "-c", "rm -rf /home/judge/workspace/* /tmp/* && pkill -9 -u judge 2>/dev/null || true")
                .exec();

        dockerClient.execStartCmd(execCreate.getId()).exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>()).awaitCompletion();
    }

    public void ensureCoreSize(LanguageContainerPool pool) {
        int currentSize = pool.getIdleQueue().size() + pool.getActiveSet().size();
        String language = getLanguageByPool(pool);
        while (currentSize < pool.getCoreSize()) {
            try {
                Container container = createContainer(language);
                container.setStatus(ContainerStatus.IDLE);
                container.setLastActiveTime(System.currentTimeMillis());
                pool.getIdleQueue().offer(container);
                currentSize++;
                log.debug("Pre-created container for language {}, current pool size: {}", language, currentSize);
            } catch (Exception e) {
                log.error("Failed to create container for language {}", language, e);
                break;
            }
        }
    }

    private String getLanguageByPool(LanguageContainerPool pool) {
        for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
            if (entry.getValue() == pool) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Scheduled(fixedDelayString = "${judge.health-check.interval-seconds:60}000")
    public void healthCheck() {
        if (!dockerConnected.get()) {
            if (checkDockerConnection()) {
                if (!initialized.get()) {
                    initializePools();
                    log.info("Docker reconnected, container pool initialized");
                }
            }
            return;
        }

        if (!initialized.get()) {
            return;
        }

        log.debug("Starting container health check");

        for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
            LanguageContainerPool pool = entry.getValue();

            pool.getIdleQueue().removeIf(container -> {
                long idleTime = (System.currentTimeMillis() - container.getLastActiveTime()) / 1000;
                if (idleTime > pool.getMaxIdleTimeSeconds()) {
                    log.info("Destroying idle container {} exceeded max idle time ({}s > {}s)",
                            container.getContainerId(), idleTime, pool.getMaxIdleTimeSeconds());
                    destroyContainer(container);
                    return true;
                }
                if (!isHealthy(container)) {
                    log.warn("Destroying unhealthy idle container {}", container.getContainerId());
                    destroyContainer(container);
                    return true;
                }
                return false;
            });

            pool.getActiveSet().removeIf(container -> {
                long activeTime = (System.currentTimeMillis() - container.getLastActiveTime()) / 1000;
                if (activeTime > healthCheckConfig.getMaxActiveTimeoutSeconds()) {
                    log.warn("Destroying active container {} exceeded max active timeout ({}s)",
                            container.getContainerId(), activeTime);
                    destroyContainer(container);
                    return true;
                }
                return false;
            });

            ensureCoreSize(pool);
        }

        smartScaleDown();

        cleanOrphanedContainers();

        try {
            dockerClient.pingCmd().exec();
        } catch (Exception e) {
            log.error("Docker daemon health check failed: {}", e.getMessage());
            dockerConnected.set(false);
        }
    }

    private void cleanOrphanedContainers() {
        try {
            java.util.Set<String> managedIds = new java.util.HashSet<>();
            for (LanguageContainerPool pool : poolMap.values()) {
                for (Container c : pool.getIdleQueue()) managedIds.add(c.getContainerId());
                for (Container c : pool.getActiveSet()) managedIds.add(c.getContainerId());
            }

            java.util.List<com.github.dockerjava.api.model.Container> runningContainers =
                    dockerClient.listContainersCmd()
                            .withStatusFilter(java.util.Collections.singleton("running"))
                            .exec();

            for (com.github.dockerjava.api.model.Container dc : runningContainers) {
                String name = dc.getNames() != null && dc.getNames().length > 0 ? dc.getNames()[0] : "";
                if (name.startsWith("/judge-") && !managedIds.contains(dc.getId())) {
                    log.warn("Found orphaned judge container: id={}, names={}, destroying it",
                            dc.getId(), java.util.Arrays.toString(dc.getNames()));
                    try {
                        dockerClient.stopContainerCmd(dc.getId()).exec();
                    } catch (Exception e) {
                        log.debug("Failed to stop orphaned container {}: {}", dc.getId(), e.getMessage());
                    }
                    try {
                        dockerClient.removeContainerCmd(dc.getId()).withForce(true).exec();
                        log.info("Destroyed orphaned container {}", dc.getId());
                    } catch (Exception e) {
                        log.debug("Failed to remove orphaned container {}: {}", dc.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to clean orphaned containers: {}", e.getMessage());
        }
    }

    private void smartScaleDown() {
        int totalActive = 0;
        int totalIdle = 0;
        int totalContainers = 0;
        int totalCoreSize = 0;

        for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
            LanguageContainerPool pool = entry.getValue();
            int active = pool.getActiveSet().size();
            int idle = pool.getIdleQueue().size();
            totalActive += active;
            totalIdle += idle;
            totalContainers += active + idle;
            totalCoreSize += pool.getCoreSize();
        }

        if (totalContainers == 0) return;

        int utilizationPercent = totalContainers > 0 ? (totalActive * 100) / totalContainers : 0;
        log.info("Container pool utilization: {}/{} active ({}%), coreSize={}, borrow/return={}/{}",
                totalActive, totalContainers, utilizationPercent, totalCoreSize,
                totalBorrowCount.get(), totalReturnCount.get());

        if (utilizationPercent < LOW_UTILIZATION_THRESHOLD_PERCENT && totalContainers > totalCoreSize) {
            consecutiveLowUtilizationChecks++;
            log.info("Low utilization detected: {}% (consecutive checks: {}/{})",
                    utilizationPercent, consecutiveLowUtilizationChecks, LOW_UTILIZATION_CONSECUTIVE_CHECKS);

            if (consecutiveLowUtilizationChecks >= LOW_UTILIZATION_CONSECUTIVE_CHECKS) {
                int excess = totalContainers - totalCoreSize;
                if (excess > 0) {
                    int toRemove = Math.max(1, excess / 2);
                    log.info("Auto-scaling down: removing {} excess containers (total={} core={} utilization={}%)",
                            toRemove, totalContainers, totalCoreSize, utilizationPercent);
                    scaleDown(toRemove);
                }
                consecutiveLowUtilizationChecks = 0;
            }
        } else {
            consecutiveLowUtilizationChecks = 0;
        }
    }

    private void scaleDown(int count) {
        int removed = 0;
        for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
            if (removed >= count) break;
            LanguageContainerPool pool = entry.getValue();
            String language = entry.getKey();

            int active = pool.getActiveSet().size();
            int idle = pool.getIdleQueue().size();
            int currentTotal = active + idle;
            int minKeep = Math.max(0, pool.getCoreSize() - active);

            while (removed < count && idle > minKeep) {
                Container container = pool.getIdleQueue().poll();
                if (container != null) {
                    log.info("Auto-scale down: destroying idle container {} for language {} (active={}, idle={}, minKeep={})",
                            container.getContainerId(), language, active, idle, minKeep);
                    destroyContainer(container);
                    removed++;
                    idle--;
                } else {
                    break;
                }
            }
        }
        if (removed > 0) {
            log.info("Auto-scale down completed: removed {} excess containers", removed);
        }
    }

    public Container createContainer(String language) {
        LanguageContainerPool pool = poolMap.get(language);
        String image = pool.getImage();

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withMemory((long) judgeMachineConfig.getContainerMemoryMb() * 1024 * 1024)
                .withMemorySwappiness(0L)
                .withCpuCount((long) judgeMachineConfig.getContainerCpus())
                .withPidsLimit(50L)
                .withCapDrop(Capability.ALL)
                .withTmpFs(Map.of(
                        "/tmp", "rw,noexec,nosuid,size=64m"
                ));

        String containerName = "judge-" + language + "-" + System.currentTimeMillis();
        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withCmd("sh", "-c", "while true; do sleep 3600; done")
                .exec();

        String containerId = response.getId();
        dockerClient.startContainerCmd(containerId).exec();

        try {
            ExecCreateCmdResponse mkdirExec = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("sh", "-c", "mkdir -p /home/judge/workspace && chown judge:judge /home/judge/workspace")
                    .exec();
            dockerClient.execStartCmd(mkdirExec.getId()).exec(
                    new com.github.dockerjava.api.async.ResultCallback.Adapter<>()).awaitCompletion(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to ensure workspace directory in container {}: {}", containerId, e.getMessage());
        }

        Container container = Container.builder()
                .containerId(containerId)
                .language(language)
                .status(ContainerStatus.IDLE)
                .reuseCount(0)
                .lastActiveTime(System.currentTimeMillis())
                .createdAt(System.currentTimeMillis())
                .build();

        log.info("Created container {} for language {}", containerId, language);
        return container;
    }

    public void destroyContainer(Container container) {
        try {
            dockerClient.stopContainerCmd(container.getContainerId()).exec();
        } catch (Exception e) {
            log.warn("Failed to stop container {}", container.getContainerId(), e);
        }
        try {
            dockerClient.removeContainerCmd(container.getContainerId()).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove container {}", container.getContainerId(), e);
        }
        log.info("Destroyed container {}", container.getContainerId());
    }

    public boolean isHealthy(Container container) {
        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(container.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("echo", "hello")
                    .exec();

            var callback = dockerClient.execStartCmd(execCreate.getId())
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>());
            callback.awaitCompletion(5, TimeUnit.SECONDS);

            var inspectResponse = dockerClient.inspectExecCmd(execCreate.getId()).exec();
            return inspectResponse.getExitCodeLong() != null && inspectResponse.getExitCodeLong() == 0L;
        } catch (Exception e) {
            log.warn("Health check failed for container {}", container.getContainerId(), e);
            return false;
        }
    }

    public LanguageContainerPool getPool(String language) {
        return poolMap.get(language);
    }

    public long getContainerMemoryUsageKb(String containerId) {
        try {
            String output = execInContainerForOutput(containerId,
                    "cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes 2>/dev/null || " +
                    "cat /sys/fs/cgroup/memory.peak 2>/dev/null || echo -1");
            long bytes = Long.parseLong(output.trim());
            if (bytes < 0) {
                log.warn("Could not read memory stats from container {}", containerId);
                return 0L;
            }
            return bytes / 1024;
        } catch (Exception e) {
            log.warn("Failed to get memory usage for container {}: {}", containerId, e.getMessage());
            return 0L;
        }
    }

    public void resetContainerMemoryStats(String containerId) {
        try {
            execInContainerForOutput(containerId,
                    "echo 0 > /sys/fs/cgroup/memory/memory.max_usage_in_bytes 2>/dev/null; " +
                    "echo 0 > /sys/fs/cgroup/memory.peak 2>/dev/null; true");
        } catch (Exception e) {
            log.debug("Could not reset memory stats for container {}: {}", containerId, e.getMessage());
        }
    }

    private String execInContainerForOutput(String containerId, String cmd) {
        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("sh", "-c", cmd)
                    .exec();

            StringBuilder stdout = new StringBuilder();
            var callback = new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                @Override
                public void onNext(com.github.dockerjava.api.model.Frame frame) {
                    if (frame.getStreamType() == com.github.dockerjava.api.model.StreamType.STDOUT) {
                        stdout.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                    }
                }
            };

            dockerClient.execStartCmd(execCreate.getId()).exec(callback).awaitCompletion(10, TimeUnit.SECONDS);
            return stdout.toString().trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing command in container " + containerId, e);
        }
    }

    public int scaleUp(String language, int count) {
        LanguageContainerPool pool = poolMap.get(language);
        if (pool == null) return 0;

        long now = System.currentTimeMillis();
        if (now - lastScaleUpTime < SCALE_UP_COOLDOWN_MS) {
            log.debug("Scale up cooldown active, skipping scale up for language {}", language);
            return 0;
        }

        int created = 0;
        for (int i = 0; i < count; i++) {
            int currentTotal = pool.getIdleQueue().size() + pool.getActiveSet().size();
            if (currentTotal >= pool.getMaxSize()) break;
            try {
                Container container = createContainer(language);
                if (container != null) {
                    pool.getIdleQueue().offer(container);
                    created++;
                }
            } catch (Exception e) {
                log.warn("Failed to scale up container for language {}: {}", language, e.getMessage());
                break;
            }
        }
        if (created > 0) {
            lastScaleUpTime = System.currentTimeMillis();
            log.info("Scaled up {} containers for language {}", created, language);
        }
        return created;
    }

    public Map<String, Map<String, Integer>> getPoolStats() {
        Map<String, Map<String, Integer>> stats = new HashMap<>();
        for (Map.Entry<String, LanguageContainerPool> entry : poolMap.entrySet()) {
            LanguageContainerPool pool = entry.getValue();
            Map<String, Integer> poolStats = new HashMap<>();
            poolStats.put("idle", pool.getIdleQueue().size());
            poolStats.put("active", pool.getActiveSet().size());
            poolStats.put("coreSize", pool.getCoreSize());
            poolStats.put("maxSize", pool.getMaxSize());
            stats.put(entry.getKey(), poolStats);
        }
        return stats;
    }

    public int getTotalIdleCount() {
        return poolMap.values().stream()
                .mapToInt(pool -> pool.getIdleQueue().size())
                .sum();
    }

    public int getTotalActiveCount() {
        return poolMap.values().stream()
                .mapToInt(pool -> pool.getActiveSet().size())
                .sum();
    }

    public int getTotalContainerCount() {
        return poolMap.values().stream()
                .mapToInt(pool -> pool.getIdleQueue().size() + pool.getActiveSet().size())
                .sum();
    }
}
