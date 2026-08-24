package com.rain.zhixueproblem.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZhixueProblemTag {
    private Long id;
    private String name;
    private String nameEn;
    private String color;
    private Integer problemCount;
    private LocalDateTime createdAt;
}
