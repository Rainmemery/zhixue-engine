package com.rain.zhixueuser.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.service.AuthService;
import com.rain.zhixueuser.service.UserMangementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserMangementService userMangementService;

    @PostMapping("/register")
    public Result register(@RequestBody Map<String, String> map) {
        //log.info("前端访问了注册服务");
        try {
            User user = authService.Register(map.get("username"), map.get("email"), map.get("password"),map.get("phone"), map.get("realname"));
            if(user == null){
                return Result.wrong("注册失败，请联系管理员");
            }
            user = authService.selectByUsername(map.get("username"));
            Map<String,Object> data = new HashMap<>();
            data.put("token",authService.generateJwtToken(user.getId()));//set token
            data.put("refreshToken",authService.generateRefreshToken(user.getId()));//refreshToken
            //Map<String,Object> userData = new HashMap<>();

            data.put("user",user);
            return Result.success(data,"success");
        }catch (Exception e){
            return Result.wrong("注册过程系统出错");
        }
    }

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, String> map) {
        //log.info("前端访问了登陆服务 ");
        try {
            //login
            User user = authService.Login(map.get("username"),map.get("password"));
            if(user == null){
                return Result.wrong("用户或密码错误");
            }
            Map<String,Object> data = new HashMap<>();
            data.put("token",authService.generateJwtToken(user.getId()));
            data.put("refreshToken",authService.generateRefreshToken(user.getId()));
            data.put("user",user);
            return Result.success(data,"success");
        }catch (Exception e){
            return Result.wrong("注册过程系统出错");
        }
    }
    @PostMapping("/refresh")
    public Result refresh(@RequestHeader("Authorization") String authHeader) {
        try {
            String refreshToken = authHeader.replaceFirst("^Bearer\\s+", "").trim();
            Long id=authService.refresh(refreshToken);
            if(id==null){
                return Result.wrong("刷新失败");
            }
            Map<String,Object> data = new HashMap<>();
            data.put("token",authService.generateJwtToken(id));
            data.put("refreshToken",authService.generateRefreshToken(id));
            return Result.success(data,"success");
        }catch (Exception e){
            return Result.wrong("刷新失败");
        }
    }
    @GetMapping("/validate")
    public Result validate(@RequestHeader("Authorization") String authHeader) {
        String Token = authHeader.replaceFirst("^Bearer\\s+", "").trim();
        Long id= authService.validate(Token);
        if (id==null){
            return Result.wrong("认证失败");
        }
        Map<String,Object> data = new HashMap<>();
        data.put("valid",true);
        data.put("user",userMangementService.getById(id));
        return Result.success(data,"success");
    }
}
