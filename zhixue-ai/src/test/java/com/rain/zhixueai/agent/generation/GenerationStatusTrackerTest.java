package com.rain.zhixueai.agent.generation;

import com.rain.zhixueai.agent.generation.GenerationStatusTracker.GenerationState;
import com.rain.zhixueai.agent.generation.GenerationStatusTracker.GenerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerationStatusTrackerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private GenerationStatusTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new GenerationStatusTracker();
        ReflectionTestUtils.setField(tracker, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void initialize_createsQueuedState() {
        tracker.initialize("test-task-1", 1L);
        verify(valueOperations).set(contains("test-task-1"), anyString(), anyLong(), any());
    }

    @Test
    void updateStatus_changesStatus() {
        when(valueOperations.get(anyString())).thenReturn(
            "{\"taskId\":\"test-1\",\"userId\":1,\"status\":\"QUEUED\",\"currentPhase\":0,\"totalPhases\":4,\"progress\":0.0,\"retryCount\":0}"
        );
        tracker.updateStatus("test-1", GenerationStatus.GENERATING_PROBLEM);
        verify(valueOperations, atLeastOnce()).set(contains("test-1"), anyString(), anyLong(), any());
    }

    @Test
    void get_nonExistentTask_returnsNull() {
        when(valueOperations.get(anyString())).thenReturn(null);
        GenerationState state = tracker.get("non-existent");
        assertNull(state);
    }

    @Test
    void setError_setsFailedStatus() {
        when(valueOperations.get(anyString())).thenReturn(
            "{\"taskId\":\"test-1\",\"userId\":1,\"status\":\"GENERATING_PROBLEM\",\"currentPhase\":1,\"totalPhases\":4,\"progress\":0.25,\"retryCount\":0}"
        );
        tracker.setError("test-1", "Generation failed");
        verify(valueOperations).set(contains("test-1"), argThat(s -> s.contains("FAILED")), anyLong(), any());
    }
}
