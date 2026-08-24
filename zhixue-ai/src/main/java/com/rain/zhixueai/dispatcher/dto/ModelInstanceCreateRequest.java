package com.rain.zhixueai.dispatcher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 创建模型实例请求DTO
 * 用于接收创建模型实例的请求数据
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInstanceCreateRequest {
    
    @NotNull(message = "模型组ID不能为空")
    private Long groupId;
    
    @NotBlank(message = "实例名称不能为空")
    @Size(max = 100, message = "实例名称长度不能超过100个字符")
    private String name;
    
    @NotBlank(message = "API端点不能为空")
    @Size(max = 500, message = "API端点长度不能超过500个字符")
    private String apiEndpoint;
    
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过100个字符")
    private String modelName;
    
    @Size(max = 200, message = "API密钥长度不能超过200个字符")
    private String apiKey;
    
    @Min(value = 1, message = "权重最小为1")
    @Max(value = 100, message = "权重最大为100")
    private Integer weight;
    
    @Min(value = 1, message = "最大并发数最小为1")
    @Max(value = 1000, message = "最大并发数最大为1000")
    private Integer maxConcurrent;
    
    public Integer getWeightOrDefault() {
        return weight != null ? weight : 1;
    }
    
    public Integer getMaxConcurrentOrDefault() {
        return maxConcurrent != null ? maxConcurrent : 10;
    }
}
