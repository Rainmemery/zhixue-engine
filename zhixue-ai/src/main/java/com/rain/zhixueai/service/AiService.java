package com.rain.zhixueai.service;

import com.rain.zhixueai.dto.AgentChatResponse;
import com.rain.zhixueai.dto.AgentTaskCreateResult;
import com.rain.zhixueai.dto.AgentTaskProgress;
import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.dto.AiResponse;
import com.rain.zhixueai.enums.AiModelType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI服务接口
 * 定义所有AI相关的核心业务方法
 * 
 * @author rain
 * @since 2026-02-05
 */
@Service
public interface AiService {
    
    /**
     * 流式聊天接口
     * 
     * @param request AI请求参数
     * @return SSE发射器
     */
    SseEmitter streamChat(AiRequest request);
    
    /**
     * 流式代码解释接口
     * 
     * @param request AI请求参数
     * @return SSE发射器
     */
    SseEmitter streamExplain(AiRequest request);
    
    /**
     * 流式代码评审接口
     * 
     * @param request AI请求参数
     * @return SSE发射器
     */
    SseEmitter streamReview(AiRequest request);

    /**
     * 智能重构代码接口（非流式）
     *
     * @param request AI请求参数
     * @return 重构结果
     */
    Map<String, Object> refactorCode(AiRequest request);

    /**
     * 代码智能补全接口（非流式）
     *
     * @param request AI请求参数
     * @return 补全结果
     */
    Map<String, Object> completeCode(AiRequest request);

    /**
     * 流式智能提问者接口
     * 
     * @param request AI请求参数
     * @return SSE发射器
     */
    SseEmitter streamQuestioner(AiRequest request);

    SseEmitter streamAgentChat(AiRequest request);

    AgentChatResponse agentChatSync(AiRequest request);

    AgentTaskCreateResult createAgentTask(AiRequest request);

    AgentTaskProgress getAgentTaskProgress(String taskId, int afterIndex);

    Map<String, Object> getGenerationStatus(String taskId);
    
    /**
     * 切换当前使用的AI模型
     * 
     * @param modelType 模型类型
     * @throws IllegalArgumentException 当模型类型不支持或不可用时抛出
     */
    void switchModel(AiModelType modelType) throws IllegalArgumentException;
    
    /**
     * 获取当前模型状态
     * 
     * @return 模型状态信息
     */
    Map<String, Object> getModelStatus();
    
    /**
     * 获取当前使用的模型类型
     * 
     * @return 当前模型类型
     */
    AiModelType getCurrentModelType();

    /**
     * 确认消息段已接收
     * 
     * @param taskId 任务ID
     * @param sequenceNumber 消息段序列号
     * @param checksum 校验和（可选）
     * @return 确认结果
     */
    Map<String, Object> acknowledgeSegment(String taskId, int sequenceNumber, String checksum);

    /**
     * 批量确认消息段已接收
     * 
     * @param taskId 任务ID
     * @param sequenceNumbers 消息段序列号列表
     * @return 确认结果
     */
    Map<String, Object> acknowledgeSegments(String taskId, List<Integer> sequenceNumbers);

    /**
     * 获取未确认的消息段（用于重传）
     * 
     * @param taskId 任务ID
     * @return 未确认的消息段列表
     */
    Map<String, Object> getUnacknowledgedSegments(String taskId);

    /**
     * 从指定序列号恢复消息段
     * 
     * @param taskId 任务ID
     * @param fromSequenceNumber 起始序列号（不包含）
     * @return 恢复的消息段列表
     */
    Map<String, Object> recoverSegments(String taskId, int fromSequenceNumber);

    /**
     * 获取任务的恢复状态
     * 
     * @param taskId 任务ID
     * @return 恢复状态信息
     */
    Map<String, Object> getRecoveryStatus(String taskId);
}
