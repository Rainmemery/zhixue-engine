package com.rain.zhixueproblem.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZhixueProblem {
    private Long id;
    private String title;
    private String titleEn;
    private String description;
    private String inputDescription;
    private String outputDescription;
    private String hint;
    private String source;
    private String difficulty;
    private String problemType;
    private Long categoryId;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
    private String templateCode;
    private String solutionCode;
    private Boolean isPublic;
    private Integer viewCount;
    private Integer submitCount;
    private Integer acceptedCount;
    private Double acceptanceRate;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    @JsonIgnore
    private List<ZhixueProblemTag> tags;
    @JsonProperty("tags")
    private List<String> tagNames;
    private List<Long> tagIds;
    private List<ZhixueProblemSample> samples;
    @JsonProperty("category")
    private String categoryName;
    private ZhixueUserProblemStatus userStatus;

    @JsonProperty("isActive")
    public Boolean getIsActive() {
        return isPublic;
    }
}
