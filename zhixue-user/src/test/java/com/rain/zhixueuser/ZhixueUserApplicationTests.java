package com.rain.zhixueuser;

import com.rain.zhixuecommon.utils.SHA256EncryptUtil;
import com.rain.zhixueuser.entity.UserProfiles;
import com.rain.zhixueuser.mapper.UserProfilesMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@Slf4j
@SpringBootTest
class ZhixueUserApplicationTests {

    @Autowired
    private UserProfilesMapper userProfilesMapper;

//    @MyTest
//    void contextLoads() {
//        UserProfiles userProfiles = userProfilesMapper.selectByUserId(15L);
//        System.out.println(userProfiles);
//    }

}
