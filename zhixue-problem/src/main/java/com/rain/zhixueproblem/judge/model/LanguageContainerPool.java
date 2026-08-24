package com.rain.zhixueproblem.judge.model;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

public class LanguageContainerPool {
    private final BlockingQueue<Container> idleQueue = new LinkedBlockingQueue<>();
    private final Set<Container> activeSet = ConcurrentHashMap.newKeySet();
    private final Semaphore semaphore;
    private final int coreSize;
    private final int maxSize;
    private final int maxIdleTimeSeconds;
    private final int borrowTimeoutSeconds;
    private final int maxReuseCount;
    private final String image;

    public LanguageContainerPool(int coreSize, int maxSize, int maxIdleTimeSeconds,
                                 int borrowTimeoutSeconds, int maxReuseCount, String image) {
        this.coreSize = coreSize;
        this.maxSize = maxSize;
        this.maxIdleTimeSeconds = maxIdleTimeSeconds;
        this.borrowTimeoutSeconds = borrowTimeoutSeconds;
        this.maxReuseCount = maxReuseCount;
        this.image = image;
        this.semaphore = new Semaphore(maxSize);
    }

    public BlockingQueue<Container> getIdleQueue() {
        return idleQueue;
    }

    public Set<Container> getActiveSet() {
        return activeSet;
    }

    public int getCoreSize() {
        return coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getMaxIdleTimeSeconds() {
        return maxIdleTimeSeconds;
    }

    public int getBorrowTimeoutSeconds() {
        return borrowTimeoutSeconds;
    }

    public int getMaxReuseCount() {
        return maxReuseCount;
    }

    public String getImage() {
        return image;
    }

    public Semaphore getSemaphore() {
        return semaphore;
    }
}
