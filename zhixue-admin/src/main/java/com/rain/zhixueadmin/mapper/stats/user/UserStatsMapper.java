package com.rain.zhixueadmin.mapper.stats.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface UserStatsMapper {

    Long countTotalUsers();

    Long countActiveUsers(@Param("days") int days);

    Long countNewUsersToday();

    Long countNewUsersByDate(@Param("date") LocalDate date);

    Double calculateGrowthRate(@Param("days") int days);
}
