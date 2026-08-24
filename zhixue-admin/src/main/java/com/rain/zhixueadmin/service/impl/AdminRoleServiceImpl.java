package com.rain.zhixueadmin.service.impl;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.AdminRole;
import com.rain.zhixueadmin.mapper.AdminRoleMapper;
import com.rain.zhixueadmin.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {
    
    private final AdminRoleMapper adminRoleMapper;

    @Override
    public AdminRole createRole(AdminRole role) {
        adminRoleMapper.insert(role);
        return role;
    }

    @Override
    public AdminRole updateRole(AdminRole role) {
        adminRoleMapper.updateById(role);
        return adminRoleMapper.selectById(role.getId());
    }

    @Override
    public void deleteRole(Long id) {
        adminRoleMapper.deleteById(id);
    }

    @Override
    public AdminRole getRoleById(Long id) {
        return adminRoleMapper.selectById(id);
    }

    @Override
    public List<AdminRole> getAllRoles() {
        return adminRoleMapper.selectAll();
    }

    @Override
    public PageResult<AdminRole> getRoleList(String search, Integer page, Integer size) {
        int offset = (page - 1) * size;
        List<AdminRole> items = adminRoleMapper.selectPage(offset, size, search);
        long total = adminRoleMapper.countAll(search);
        return PageResult.<AdminRole>builder()
                .items(items)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }
}
