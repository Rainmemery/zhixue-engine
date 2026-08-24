package com.rain.zhixueadmin.service.impl;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.AdminUser;
import com.rain.zhixueadmin.mapper.AdminUserMapper;
import com.rain.zhixueadmin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    
    private final AdminUserMapper adminUserMapper;

    @Override
    public AdminUser createAdminUser(AdminUser user) {
        user.setStatus("active");
        adminUserMapper.insert(user);
        return user;
    }

    @Override
    public AdminUser updateAdminUser(AdminUser user) {
        adminUserMapper.updateById(user);
        return adminUserMapper.selectById(user.getId());
    }

    @Override
    public void deleteAdminUser(Long id) {
        adminUserMapper.deleteById(id);
    }

    @Override
    public void batchDeleteAdminUsers(List<Long> ids) {
        adminUserMapper.batchDelete(ids);
    }

    @Override
    public AdminUser getAdminUserById(Long id) {
        return adminUserMapper.selectById(id);
    }

    @Override
    public PageResult<AdminUser> getAdminUserList(String username, String email, Long roleId, String status, Integer page, Integer size) {
        HashMap<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("username", username);
        params.put("email", email);
        params.put("roleId", roleId);
        params.put("status", status);
        
        List<AdminUser> items = adminUserMapper.selectList(params);
        int total = adminUserMapper.countList(params);
        
        return PageResult.<AdminUser>builder()
                .items(items)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }
}
