package com.rain.zhixueuser.service.impl;

import com.rain.zhixuecommon.utils.SHA256EncryptUtil;
import com.rain.zhixueuser.dto.AdminUserQueryRequest;
import com.rain.zhixueuser.dto.PageResult;
import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.mapper.AdminUserMapper;
import com.rain.zhixueuser.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;

    @Override
    public PageResult<User> getUserList(AdminUserQueryRequest request) {
        log.info("查询用户列表 - request: {}", request);
        
        int offset = (request.getPage() - 1) * request.getSize();
        List<User> users = adminUserMapper.selectUserList(
            request.getUsername(),
            request.getEmail(),
            request.getStatus(),
            request.getSearch(),
            offset,
            request.getSize()
        );
        
        Long total = adminUserMapper.countUsers(
            request.getUsername(),
            request.getEmail(),
            request.getStatus(),
            request.getSearch()
        );
        
        return new PageResult<>(users, total, request.getPage(), request.getSize());
    }

    @Override
    public User getUserById(Long id) {
        log.info("查询用户详情 - id: {}", id);
        return adminUserMapper.selectById(id);
    }

    @Override
    @Transactional
    public User createUser(User user) {
        log.info("创建用户 - username: {}", user.getUsername());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsActive((short) 1);
        adminUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        log.info("更新用户 - id: {}", user.getId());
        user.setUpdatedAt(LocalDateTime.now());
        adminUserMapper.updateById(user);
        return adminUserMapper.selectById(user.getId());
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        log.info("删除用户 - id: {}", id);
        adminUserMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDeleteUsers(List<Long> ids) {
        log.info("批量删除用户 - ids: {}", ids);
        adminUserMapper.batchDelete(ids);
    }

    @Override
    @Transactional
    public boolean updatePassword(Long id, String newPassword) {
        try {
            String encryptedPassword = SHA256EncryptUtil.encrypt(newPassword);
            return adminUserMapper.updatePassword(id, encryptedPassword) > 0;
        } catch (Exception e) {
            log.error("密码加密失败", e);
            return false;
        }
    }
}
