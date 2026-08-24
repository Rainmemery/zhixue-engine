package com.rain.zhixueai.dispatcher.mapper.handler;

import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(SchedulingStrategy.class)
public class SchedulingStrategyTypeHandler extends BaseTypeHandler<SchedulingStrategy> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, SchedulingStrategy parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public SchedulingStrategy getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String code = rs.getString(columnName);
        return code == null ? null : SchedulingStrategy.fromCode(code);
    }

    @Override
    public SchedulingStrategy getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String code = rs.getString(columnIndex);
        return code == null ? null : SchedulingStrategy.fromCode(code);
    }

    @Override
    public SchedulingStrategy getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String code = cs.getString(columnIndex);
        return code == null ? null : SchedulingStrategy.fromCode(code);
    }
}