package com.rain.zhixueadmin.service;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.AdminRole;

import java.util.List;

public interface AdminRoleService {
    AdminRole createRole(AdminRole role);
    
    AdminRole updateRole(AdminRole role);
    
    void deleteRole(Long id);
    
    AdminRole getRoleById(Long id);
    
    List<AdminRole> getAllRoles();
    
    PageResult<AdminRole> getRoleList(String search, Integer page, Integer size);
}
