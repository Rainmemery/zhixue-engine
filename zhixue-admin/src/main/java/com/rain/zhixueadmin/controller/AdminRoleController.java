package com.rain.zhixueadmin.controller;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.AdminRole;
import com.rain.zhixueadmin.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {
    
    private final AdminRoleService adminRoleService;

    @GetMapping
    public Map<String, Object> getRoleList(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult<AdminRole> result = adminRoleService.getRoleList(search, page, size);
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
    public Map<String, Object> getRoleById(@PathVariable Long id) {
        AdminRole role = adminRoleService.getRoleById(id);
        if (role == null) {
            return Map.of("code", 404, "message", "角色不存在");
        }
        return Map.of("code", 200, "message", "success", "data", role);
    }

    @PostMapping
    public Map<String, Object> createRole(@RequestBody AdminRole role) {
        AdminRole created = adminRoleService.createRole(role);
        return Map.of("code", 200, "message", "创建成功", "data", created);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateRole(@PathVariable Long id, @RequestBody AdminRole role) {
        role.setId(id);
        AdminRole updated = adminRoleService.updateRole(role);
        return Map.of("code", 200, "message", "更新成功", "data", updated);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRole(@PathVariable Long id) {
        adminRoleService.deleteRole(id);
        return Map.of("code", 200, "message", "删除成功");
    }
}
