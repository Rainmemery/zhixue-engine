package com.rain.zhixueuser.mapper;

import com.rain.zhixueuser.entity.UserProfiles;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfilesMapper {
    UserProfiles selectByUserId(Long userId);
    int updateUserProfiles(UserProfiles userProfiles);
}
