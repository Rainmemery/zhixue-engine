package com.rain.zhixueuser.controller;

import com.rain.zhixueuser.dto.AdminUserQueryRequest;
import com.rain.zhixueuser.dto.PageResult;
import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Map<String, Object> getUserList(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("获取用户列表 - page: {}, size: {}, search: {}, status: {}", page, size, search, status);
        
        AdminUserQueryRequest request = new AdminUserQueryRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setStatus(status);
        request.setSearch(search);
        request.setPage(page);
        request.setSize(size);
        
        PageResult<User> result = adminUserService.getUserList(request);
        
        return Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of(
                "items", result.getItems(),
                "total", result.getTotal(),
                "page", result.getPage(),
                "size", result.getSize()
            )
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getUserById(@PathVariable Long id) {
        log.info("获取用户详情 - id: {}", id);
        User user = adminUserService.getUserById(id);
        if (user == null) {
            return Map.of("code", 404, "message", "用户不存在");
        }
        return Map.of("code", 200, "message", "success", "data", user);
    }

    @PostMapping
    public Map<String, Object> createUser(@RequestBody User user) {
        log.info("创建用户 - username: {}", user.getUsername());
        User created = adminUserService.createUser(user);
        return Map.of("code", 200, "message", "创建成功", "data", created);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody User user) {
        log.info("更新用户 - id: {}", id);
        user.setId(id);
        User updated = adminUserService.updateUser(user);
        return Map.of("code", 200, "message", "更新成功", "data", updated);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        log.info("删除用户 - id: {}", id);
        adminUserService.deleteUser(id);
        return Map.of("code", 200, "message", "删除成功");
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDeleteUsers(@RequestBody List<Long> ids) {
        log.info("批量删除用户 - ids: {}", ids);
        adminUserService.batchDeleteUsers(ids);
        return Map.of("code", 200, "message", "批量删除成功");
    }

    @PutMapping("/{id}/password")
    public Map<String, Object> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return Map.of("code", 400, "message", "新密码不能为空");
        }
        log.info("更新用户密码 - id: {}", id);
        boolean success = adminUserService.updatePassword(id, newPassword);
        if (success) {
            return Map.of("code", 200, "message", "密码更新成功");
        }
        return Map.of("code", 400, "message", "密码更新失败");
    }
}
