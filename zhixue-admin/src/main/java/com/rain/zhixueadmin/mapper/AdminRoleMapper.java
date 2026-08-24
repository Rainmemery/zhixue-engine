package com.rain.zhixueadmin.mapper;

import com.rain.zhixueadmin.entity.AdminRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminRoleMapper {
    int insert(AdminRole role);
    
    int updateById(AdminRole role);
    
    int deleteById(@Param("id") Long id);
    
    AdminRole selectById(@Param("id") Long id);
    
    List<AdminRole> selectAll();
    
    List<AdminRole> selectPage(@Param("offset") int offset, @Param("size") int size, @Param("search") String search);
    
    long countAll(@Param("search") String search);
}
