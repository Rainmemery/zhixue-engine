package com.rain.zhixueai.dispatcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rain.zhixueai.dispatcher.enums.SchedulingAction;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 调度日志实体
 * 记录模型调度过程中的操作日志
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("scheduling_log")
public class SchedulingLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long groupId;
    
    private Long instanceId;
    
    private String requestId;
    
    private SchedulingAction action;
    
    private String status;
    
    private String errorMessage;
    
    private Long durationMs;
    
    private LocalDateTime createdAt;
    
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
    
    public boolean isFailure() {
        return "FAILURE".equalsIgnoreCase(status);
    }
}
