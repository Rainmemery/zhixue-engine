package com.rain.zhixueadmin.controller;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.AdminUser;
import com.rain.zhixueadmin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/admin-users")
@RequiredArgsConstructor
public class AdminUserController {
    
    private final AdminUserService adminUserService;

    @GetMapping
    public Map<String, Object> getUserList(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult<AdminUser> result = adminUserService.getAdminUserList(username, email, roleId, status, page, size);
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
        AdminUser user = adminUserService.getAdminUserById(id);
        if (user == null) {
            return Map.of("code", 404, "message", "用户不存在");
        }
        return Map.of("code", 200, "message", "success", "data", user);
    }

    @PostMapping
    public Map<String, Object> createUser(@RequestBody AdminUser user) {
        AdminUser created = adminUserService.createAdminUser(user);
        return Map.of("code", 200, "message", "创建成功", "data", created);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody AdminUser user) {
        user.setId(id);
        AdminUser updated = adminUserService.updateAdminUser(user);
        return Map.of("code", 200, "message", "更新成功", "data", updated);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        adminUserService.deleteAdminUser(id);
        return Map.of("code", 200, "message", "删除成功");
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDeleteUsers(@RequestBody List<Long> ids) {
        adminUserService.batchDeleteAdminUsers(ids);
        return Map.of("code", 200, "message", "批量删除成功");
    }
}
