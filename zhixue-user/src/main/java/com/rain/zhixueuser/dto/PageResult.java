package com.rain.zhixueuser.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer size;

    public PageResult() {
    }

    public PageResult(List<T> items, Long total, Integer page, Integer size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
