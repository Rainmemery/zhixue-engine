package com.rain.zhixueadmin.mapper;

import com.rain.zhixueadmin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminUserMapper {
    int insert(AdminUser user);
    
    int updateById(AdminUser user);
    
    int deleteById(@Param("id") Long id);
    
    AdminUser selectById(@Param("id") Long id);
    
    AdminUser selectByUsername(@Param("username") String username);
    
    List<AdminUser> selectList(Map<String, Object> params);
    
    int countList(Map<String, Object> params);
    
    int batchDelete(@Param("ids") List<Long> ids);
}
