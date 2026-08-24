package com.rain.zhixueai.scheduling.mapper.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rain.zhixueai.scheduling.entity.CustomSchedulingStrategy;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(CustomSchedulingStrategy.StrategyCondition.class)
public class StrategyConditionHandler extends BaseTypeHandler<CustomSchedulingStrategy.StrategyCondition> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, CustomSchedulingStrategy.StrategyCondition parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, objectMapper.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("Error converting StrategyCondition to JSON", e);
        }
    }
    
    @Override
    public CustomSchedulingStrategy.StrategyCondition getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseCondition(json);
    }
    
    @Override
    public CustomSchedulingStrategy.StrategyCondition getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseCondition(json);
    }
    
    @Override
    public CustomSchedulingStrategy.StrategyCondition getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseCondition(json);
    }
    
    private CustomSchedulingStrategy.StrategyCondition parseCondition(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CustomSchedulingStrategy.StrategyCondition.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error parsing StrategyCondition from JSON", e);
        }
    }
}
