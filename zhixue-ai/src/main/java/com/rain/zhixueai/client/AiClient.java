package com.rain.zhixueai.client;

import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.dto.AiResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.function.Consumer;

/**
 * AI客户端接口
 * 定义统一的AI服务调用接口
 * 
 * @author rain
 * @since 2026-02-05
 */
public interface AiClient {
    
    /**
     * 流式聊天接口
     * 
     * @param request AI请求参数
     * @param emitter SSE发射器
     * @param responseCallback 响应回调函数
     */
    void streamChat(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback);
    
    /**
     * 流式代码解释接口
     * 
     * @param request AI请求参数
     * @param emitter SSE发射器
     * @param responseCallback 响应回调函数
     */
    void streamExplain(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback);
    
    /**
     * 流式代码评审接口
     * 
     * @param request AI请求参数
     * @param emitter SSE发射器
     * @param responseCallback 响应回调函数
     */
    void streamReview(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback);

    /**
     * 智能重构代码接口（非流式）
     *
     * @param request AI请求参数
     * @return 重构结果
     */
    Map<String, Object> refactorCode(AiRequest request);

    /**
     * 流式智能提问者接口
     * 
     * @param request AI请求参数
     * @param emitter SSE发射器
     * @param responseCallback 响应回调函数
     */
    void streamQuestioner(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback);
    
    /**
     * 检查服务是否可用
     * 
     * @return 服务是否可用
     */
    boolean isAvailable();
    
    /**
     * 获取客户端名称
     * 
     * @return 客户端名称
     */
    String getClientName();
}