package com.rain.zhixueadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserQueryRequest {
    private String username;
    private String email;
    private String role;
    private String status;
    private Integer page;
    private Integer size;
}
