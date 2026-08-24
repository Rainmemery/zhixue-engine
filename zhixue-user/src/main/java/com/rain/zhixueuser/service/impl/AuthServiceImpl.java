package com.rain.zhixueuser.service.impl;

import com.rain.zhixuecommon.utils.SHA256EncryptUtil;
import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.mapper.AuthMapper;
import com.rain.zhixueuser.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.rain.zhixuecommon.utils.JwtUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.rain.zhixuecommon.utils.SHA256EncryptUtil.*;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AuthMapper  authMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public User Register(String username, String email, String password, String phone, String realname){
        try {
            password= SHA256EncryptUtil.encrypt(password);
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPhone(phone);
            user.setPasswordHash(password);
            log.info(password);
            user.setRealName(realname);
            int val=authMapper.insertUser(user);
            if(val==0){
                return null;
            }
            
            Long userId = user.getId();
            if (userId != null) {
                authMapper.insertUserProfile(userId);
                
                List<Integer> achievementIds = authMapper.selectActiveAchievementIds();
                if (achievementIds != null && !achievementIds.isEmpty()) {
                    authMapper.initUserAchievements(userId, achievementIds);
                }
                log.info("用户初始化完成: userId={}, profile已创建, achievements已初始化", userId);
            }
            
            log.info("注册用户成功");
            user.setLastActiveDate(LocalDate.now());
            return user;
        }catch (Exception e){
            e.printStackTrace();
            return  null;
        }
    }

    @Override
    public User Login(String username, String password) {
        try {
            User user = authMapper.selectByUsername(username);
            if(user==null){
                //log.info("数据库查询不到请求的用户");
                return null;
            }
//            String rawPassword = password;
//            String encryptStr = user.getPasswordHash();
//
//            // 拆分存储的盐值和密文
//            String[] parts = encryptStr.split("\\$");
//
//            String saltHex = parts[0];
//            String targetHashHex = parts[1];
//            // 盐值转字节数组，用相同盐值重新哈希用户输入的密码
//            byte[] salt = hexToBytes(saltHex);
//            byte[] currentHashBytes = sha256Hash(rawPassword.trim(), salt);
//            String currentHashHex = bytesToHex(currentHashBytes);
//            // 对比两次密文是否一致
//            log.info("验证密码"+currentHashHex+"\n"+targetHashHex);
            if(SHA256EncryptUtil.verify(password,user.getPasswordHash())){
                //匹配成功
                return user;
            }
            return null;
        }catch (Exception e){
            e.printStackTrace();
            return  null;
        }
    }

    @Override
    public Long refresh(String token) {
        return Long.valueOf(jwtUtil.getUserIdFromRefreshToken(token));
    }

    @Override
    public Long validate(String token){
        try {
            String userIdStr = jwtUtil.getUserIdFromToken(token);
            if (userIdStr != null) {
                return Long.valueOf(userIdStr);
            }
            return null;
        } catch (Exception e) {
            log.error("验证令牌失败", e);
            return null;
        }
    }

    @Override
    public User selectByUsername(String username) {
        return authMapper.selectByUsername(username);
    }

    @Override
    // 生成JWT令牌
    public String generateJwtToken(Long id) {
        return jwtUtil.generateToken(id);
    }
    @Override
    // 生成刷新令牌
    public String generateRefreshToken(Long id) {
        return jwtUtil.generateRefreshToken(id);
    }
}
