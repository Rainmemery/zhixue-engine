package com.rain.zhixueadmin.service;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.AdminUser;

import java.util.List;

public interface AdminUserService {
    AdminUser createAdminUser(AdminUser user);
    
    AdminUser updateAdminUser(AdminUser user);
    
    void deleteAdminUser(Long id);
    
    void batchDeleteAdminUsers(List<Long> ids);
    
    AdminUser getAdminUserById(Long id);
    
    PageResult<AdminUser> getAdminUserList(String username, String email, Long roleId, String status, Integer page, Integer size);
}
