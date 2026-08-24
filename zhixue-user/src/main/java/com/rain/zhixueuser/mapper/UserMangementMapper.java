package com.rain.zhixueuser.mapper;

import com.rain.zhixueuser.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMangementMapper {
    User selectById(Long id);
    int updateById(User user);
}
