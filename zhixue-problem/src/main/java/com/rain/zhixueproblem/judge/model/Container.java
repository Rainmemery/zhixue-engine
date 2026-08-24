package com.rain.zhixueproblem.judge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Container {
    private String containerId;
    private String language;
    private ContainerStatus status;
    private int reuseCount;
    private long lastActiveTime;
    private long createdAt;
}
