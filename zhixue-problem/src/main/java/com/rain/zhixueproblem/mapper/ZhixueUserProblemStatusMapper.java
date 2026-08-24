package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueUserProblemStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueUserProblemStatusMapper {
    int insert(ZhixueUserProblemStatus zhixueUserProblemStatus);

    int updateById(ZhixueUserProblemStatus zhixueUserProblemStatus);

    ZhixueUserProblemStatus selectByUserIdAndProblemId(@Param("userId") Long userId, @Param("problemId") Long problemId);

    List<ZhixueUserProblemStatus> selectByUserId(@Param("userId") Long userId);

    List<ZhixueUserProblemStatus> selectByProblemId(@Param("problemId") Long problemId);
}
