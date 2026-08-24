package com.rain.zhixueuser.service.impl;

import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.entity.UserProfiles;
import com.rain.zhixueuser.mapper.AdminUserMapper;
import com.rain.zhixueuser.mapper.UserProfilesMapper;
import com.rain.zhixueuser.service.UserProfilesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class UserProfilesServiceImpl implements UserProfilesService {

    @Autowired
    UserProfilesMapper userProfilesMapper;

    @Autowired
    AdminUserMapper adminUserMapper;

    @Override
    public UserProfiles getUserProfiles(Long userId){
        return userProfilesMapper.selectByUserId(userId);
    }

    @Override
    public int updateUserProfiles(UserProfiles userProfiles){
        log.info("开始更新UserProfiles");
        return userProfilesMapper.updateUserProfiles(userProfiles);
    }

    @Override
    public User getUserById(Long userId) {
        return adminUserMapper.selectById(userId);
    }

    @Override
    public UserProfiles getByUserId(Long userId) {
        return userProfilesMapper.selectByUserId(userId);
    }
}
