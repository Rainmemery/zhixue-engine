package com.rain.zhixueuser.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixuecommon.utils.JwtUtil;
import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.service.UserMangementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
public class UserMangementController {
    /*
    * 暂时在controller层实现权限认证，后续再重构
    * */
    @Autowired
    private UserMangementService userMangementService;


    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/{userId}")
    public Result getUserDetail(@PathVariable("userId") Long userId,@RequestHeader("Authorization") String authHeader){
        try {
            log.info("获取到的id {}",userId);
            String jwtToken = authHeader.replaceFirst("^Bearer\\s+", "").trim();

            if(!jwtUtil.getUserIdFromToken(jwtToken).equals(userId.toString())){
                log.info("{}+{}#",userId.toString(),jwtUtil.getUserIdFromToken(jwtToken));
                return Result.wrong("无权限");
            }
            User user = userMangementService.getById(userId);
            if(user==null){
                return Result.wrong("用户不存在");
            }
            return Result.success(user,"success");
        }catch (Exception e){
            return Result.wrong("获取信息失败");
        }
    }

    @PutMapping("/{userId}")
    public Result updateUser(@PathVariable("userId") Long userId,
                             @RequestHeader("Authorization") String authHeader,
                             @RequestBody User user
    ){
        try {
            String jwtToken = authHeader.replaceFirst("^Bearer\\s+", "").trim();

            if(!jwtUtil.getUserIdFromToken(jwtToken).equals(userId.toString())){
                log.info("{}+{}#",userId.toString(),jwtUtil.getUserIdFromToken(jwtToken));
                return Result.wrong("无权限");
            }
            log.info(user.getRealName());
            user= userMangementService.update(userId,user.getRealName(),user.getPhone(),user.getSchool(),user.getMajor());
            if(user==null){
                return Result.wrong("更新信息失败");
            }
            return Result.success(user,"success");
        }catch (Exception e){
            return Result.wrong("更新信息失败");
        }
    }
}
