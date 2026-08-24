package com.rain.zhixueuser.service;

import com.rain.zhixueuser.entity.User;
import org.springframework.stereotype.Service;

@Service
public interface UserMangementService {
    User getById(Long id);
    User update(Long id,String realName,String phone,String school,String major);
}
