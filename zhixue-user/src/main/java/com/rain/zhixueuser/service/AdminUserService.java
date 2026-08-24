package com.rain.zhixueuser.service;

import com.rain.zhixueuser.dto.AdminUserQueryRequest;
import com.rain.zhixueuser.dto.PageResult;
import com.rain.zhixueuser.entity.User;

import java.util.List;

public interface AdminUserService {
    PageResult<User> getUserList(AdminUserQueryRequest request);
    User getUserById(Long id);
    User createUser(User user);
    User updateUser(User user);
    void deleteUser(Long id);
    void batchDeleteUsers(List<Long> ids);
    boolean updatePassword(Long id, String newPassword);
}
