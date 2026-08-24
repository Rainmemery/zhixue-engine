package com.rain.zhixueai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Data
@NoArgsConstructor
public class StreamMetrics {

    private AtomicLong chunksSent = new AtomicLong(0);
    private AtomicLong totalBytes = new AtomicLong(0);
    private AtomicLong startTime = new AtomicLong(0);
    private AtomicLong lastChunkTime = new AtomicLong(0);
    private AtomicLong errors = new AtomicLong(0);
    private String modelId;
    private String modelName;

    public StreamMetrics(String modelId, String modelName) {
        this.modelId = modelId;
        this.modelName = modelName;
        this.startTime.set(System.currentTimeMillis());
    }

    public void recordChunk(int bytes) {
        chunksSent.incrementAndGet();
        totalBytes.addAndGet(bytes);
        lastChunkTime.set(System.currentTimeMillis());
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public long getDuration() {
        if (startTime.get() == 0) return 0;
        return System.currentTimeMillis() - startTime.get();
    }

    public double getChunksPerSecond() {
        long duration = getDuration();
        if (duration == 0) return 0;
        return (chunksSent.get() * 1000.0) / duration;
    }

    public double getBytesPerSecond() {
        long duration = getDuration();
        if (duration == 0) return 0;
        return (totalBytes.get() * 1000.0) / duration;
    }

    public double getIntegrityRate() {
        long total = chunksSent.get() + errors.get();
        if (total == 0) return 100.0;
        return (chunksSent.get() * 100.0) / total;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("chunksSent", chunksSent.get());
        map.put("totalBytes", totalBytes.get());
        map.put("duration", getDuration());
        map.put("chunksPerSecond", Math.round(getChunksPerSecond() * 100.0) / 100.0);
        map.put("bytesPerSecond", Math.round(getBytesPerSecond() * 100.0) / 100.0);
        map.put("errors", errors.get());
        map.put("integrityRate", Math.round(getIntegrityRate() * 100.0) / 100.0);
        if (modelId != null) map.put("modelId", modelId);
        if (modelName != null) map.put("modelName", modelName);
        return map;
    }

    public StreamMetrics snapshot() {
        StreamMetrics snapshot = new StreamMetrics(modelId, modelName);
        snapshot.chunksSent.set(this.chunksSent.get());
        snapshot.totalBytes.set(this.totalBytes.get());
        snapshot.startTime.set(this.startTime.get());
        snapshot.lastChunkTime.set(this.lastChunkTime.get());
        snapshot.errors.set(this.errors.get());
        return snapshot;
    }
}
