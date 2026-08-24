package com.rain.zhixueai.test;

import com.rain.zhixueai.dto.AiResponse;
import com.rain.zhixueai.enums.StreamEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

@Slf4j
public class MyTest {
    public void processOpenAIResponse(String data) {
        try {
            // 处理SSE数据格式
            //log.info("原始数据: {}", data);
            if (data != null ) {
                String jsonData = data.trim();//data.substring(6).trim();

                // 处理结束标记
                if (!jsonData.contains("reasoning_content")) {
                    // 发送完成信号
                    //sendComplete(emitter, callback);
                    return;
                }
                // 解析JSON数据
                //log.info("JSON数据: {}", jsonData);
                var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(jsonData);
                log.info(jsonResponse.toString());
                var choices = jsonResponse.getJSONArray("choices");
                log.info("choices数组: {}", choices.toString());

                if (choices != null && !choices.isEmpty()) {
                    var choice = choices.getJSONObject(0);
                    log.info("choice对象: {}", choice);
                    var delta = choice.getJSONObject("delta");
                    log.info("delta对象: {}", delta);
                    String finishReason = choice.getString("finish_reason");
                    log.info("finishReason: {}", finishReason);

                    // 检查是否有finish_reason，则认为是结束信号
                    if (finishReason != null && !finishReason.isEmpty()) {
                        // 某些模型在finish_reason存在时表示响应结束
                        //sendComplete(emitter, callback);
                    }

                    // 如果有内容，则发送内容
                    if (delta != null) {
                        log.info("处理delta内容");

                        String content = delta.getString("content");
                        String reasoningContent = delta.getString("reasoning_content");
                        log.info("content: '{}', reasoningContent: '{}'", content, reasoningContent);

                        // 优先使用content，如果content为空且reasoning_content存在，则使用reasoning_content
                        String actualContent = content;
                        if ((actualContent == null || actualContent.isEmpty()) && reasoningContent != null) {
                            actualContent = reasoningContent;
                        }

//                        if (actualContent != null && !actualContent.isEmpty()) {
//                            AiResponse response = new AiResponse(StreamEventType.TEXT, actualContent, actualContent);
//                            sendResponse(emitter, response);
//                            if (callback != null) {
//                                callback.accept(response);
//                            }
//                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析OpenAI响应失败: {}", data, e);
        }
    }
}
