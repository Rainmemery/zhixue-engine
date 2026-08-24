package com.rain.zhixueuser.service.impl;

import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.mapper.UserMangementMapper;
import com.rain.zhixueuser.service.UserMangementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserMangemengServiceImpl implements UserMangementService {

    @Autowired
    private UserMangementMapper userMangementMapper;

    @Override
    public User getById(Long id){
        try {
            User user=userMangementMapper.selectById(id);
            return user;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public User update(Long id,String realName,String phone,String school,String major){
        try {
            User user=new User();
            user.setId(id);
            user.setRealName(realName);
            user.setPhone(phone);
            user.setSchool(school);
            user.setMajor(major);
            int val=userMangementMapper.updateById(user);
            if(val>0){
                log.info("更新成功");
            }
            user=userMangementMapper.selectById(id);
            return user;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
