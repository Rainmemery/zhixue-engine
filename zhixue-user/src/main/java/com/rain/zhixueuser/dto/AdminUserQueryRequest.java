package com.rain.zhixueuser.dto;

import lombok.Data;

@Data
public class AdminUserQueryRequest {
    private String username;
    private String email;
    private String status;
    private String search;
    private Integer page = 1;
    private Integer size = 20;
}
