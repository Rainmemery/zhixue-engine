package com.rain.zhixueproblem.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class JudgeQueueService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void enqueue(Long submissionId, Long problemId, String language, String code) {
        try {
            Map<String, Object> task = Map.of(
                    "submissionId", submissionId,
                    "problemId", problemId,
                    "language", language,
                    "code", code
            );
            String json = objectMapper.writeValueAsString(task);
            stringRedisTemplate.opsForList().leftPush("judge:queue", json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue judge task", e);
        }
    }

    public String dequeue() {
        return stringRedisTemplate.opsForList().rightPop("judge:queue");
    }

    public long getQueueSize() {
        Long size = stringRedisTemplate.opsForList().size("judge:queue");
        return size != null ? size : 0;
    }
}
