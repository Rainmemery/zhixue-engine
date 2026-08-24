package com.rain.zhixueuser.service;

import com.rain.zhixueuser.entity.User;
import com.rain.zhixueuser.entity.UserProfiles;
import org.springframework.stereotype.Service;

@Service
public interface UserProfilesService {
    UserProfiles getUserProfiles(Long userId);
    int updateUserProfiles(UserProfiles userProfiles);
    User getUserById(Long userId);
    UserProfiles getByUserId(Long userId);
}
