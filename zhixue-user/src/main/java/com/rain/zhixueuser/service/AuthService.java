package com.rain.zhixueuser.service;

import com.rain.zhixueuser.entity.User;
import org.springframework.stereotype.Service;

@Service
public interface AuthService {
    User Register(String username, String email, String password, String phone, String realname);
    User Login(String username, String password);
    Long refresh(String token);
    Long validate(String token);
    User selectByUsername(String username);
    String generateJwtToken(Long id);
    String generateRefreshToken(Long id);
}
