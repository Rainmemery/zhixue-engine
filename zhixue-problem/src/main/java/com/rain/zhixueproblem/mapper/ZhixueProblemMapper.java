package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueProblem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ZhixueProblemMapper {
    int insert(ZhixueProblem zhixueProblem);

    int updateById(ZhixueProblem zhixueProblem);

    int deleteById(@Param("id") Long id);

    ZhixueProblem selectById(@Param("id") Long id);

    List<ZhixueProblem> selectList(Map<String, Object> params);

    int countList(Map<String, Object> params);

    List<ZhixueProblem> selectAdminList(Map<String, Object> params);

    int countAdminList(Map<String, Object> params);

    int updateAcceptanceRate(@Param("id") Long id, @Param("rate") Double rate);

    int incrementSubmitCount(@Param("id") Long id);

    int incrementAcceptedCount(@Param("id") Long id);
}
