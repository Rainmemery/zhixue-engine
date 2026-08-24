package com.rain.zhixueuser.mapper;

import com.rain.zhixueuser.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserMapper {
    List<User> selectUserList(
        @Param("username") String username,
        @Param("email") String email,
        @Param("status") String status,
        @Param("search") String search,
        @Param("offset") Integer offset,
        @Param("size") Integer size
    );
    
    Long countUsers(
        @Param("username") String username,
        @Param("email") String email,
        @Param("status") String status,
        @Param("search") String search
    );
    
    User selectById(@Param("id") Long id);
    
    int insert(User user);
    
    int updateById(User user);
    
    int deleteById(@Param("id") Long id);
    
    int batchDelete(@Param("ids") List<Long> ids);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
