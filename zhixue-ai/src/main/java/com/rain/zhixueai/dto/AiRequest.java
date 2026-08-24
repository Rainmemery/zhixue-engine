package com.rain.zhixueai.dto;

import com.rain.zhixueai.enums.AiRole;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI请求基类
 * 所有AI请求的通用属性和配置
 * 
 * @author rain
 * @since 2026-02-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequest {
    
    /**
     * 对话消息历史
     */
    private List<AiMessage> messages;
    
    /**
     * AI角色类型
     */
    private AiRole role;
    
    /**
     * 是否启用流式传输
     */
    private Boolean stream;
    
    /**
     * 温度参数 (0-1)，控制输出随机性
     */
    private Double temperature;
    
    /**
     * 最大令牌数
     */
    private Integer maxTokens;
    
    /**
     * 代码内容 (用于代码相关接口)
     */
    private String code;
    
    /**
     * 编程语言
     */
    private String language;

    /**
     * 解释类型 (用于解释接口)
     */
    private String explanationType;
    
    /**
     * 详细程度 (用于解释接口)
     */
    private String detailLevel;
    
    /**
     * 是否包含建议 (用于评审接口)
     */
    private Boolean includeSuggestions;
    
    /**
     * 上下文信息 (用于提问者接口)
     */
    private String context;
    
    /**
     * 代码选择范围
     */
    private CodeSelection selection;
    
    /**
     * 模块代码 (用于获取模块配置的知识库)
     */
    private String moduleCode;
    
    /**
     * 知识库ID列表 (可选，不指定则使用模块配置)
     */
    private List<Long> knowledgeBaseIds;
    
    /**
     * 是否启用RAG检索
     */
    private Boolean enableRag;
    
    private Long userId;

    private String authToken;
    
    /**
     * 代码选择范围内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeSelection {
        /**
         * 起始行号
         */
        private Integer startLine;
        
        /**
         * 结束行号
         */
        private Integer endLine;
        
        /**
         * 选中的文本内容
         */
        private String selectedText;
        
        /**
         * 编程语言
         */
        private String language;
        
        /**
         * 选中代码之前的上下文
         */
        private String contextBefore;
        
        /**
         * 选中代码之后的上下文
         */
        private String contextAfter;
        
        /**
         * 完整的文件代码
         */
        private String fullCode;
        
        /**
         * 文件总行数
         */
        private Integer totalLines;
        
        /**
         * 光标所在行号
         */
        private Integer cursorLine;
        
        /**
         * 光标所在列号
         */
        private Integer cursorColumn;
    }
    
    /**
     * 获取温度参数，默认0.7
     * 
     * @return 温度参数
     */
    public Double getTemperatureOrDefault() {
        return temperature != null ? temperature : 0.7;
    }
    
    /**
     * 获取最大令牌数，默认1000
     * 
     * @return 最大令牌数
     */
    public Integer getMaxTokensOrDefault() {
        return maxTokens != null ? maxTokens : 1000;
    }
    
    /**
     * 获取流式标志，默认false
     * 
     * @return 是否流式
     */
    public Boolean getStreamOrDefault() {
        return stream != null ? stream : false;
    }
    
    /**
     * 验证请求参数
     * 
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    public void validate() throws IllegalArgumentException {
        // 对于代码相关的请求，不需要消息列表验证
        boolean isCodeRelatedRequest = (code != null && !code.trim().isEmpty()) && 
                                     (language != null && !language.trim().isEmpty());
        
        if (!isCodeRelatedRequest && (messages == null || messages.isEmpty())) {
            throw new IllegalArgumentException("消息列表不能为空");
        }
        
        if (temperature != null && (temperature < 0 || temperature > 1)) {
            throw new IllegalArgumentException("温度参数必须在0-1范围内");
        }
        
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("最大令牌数必须大于0");
        }
    }
}