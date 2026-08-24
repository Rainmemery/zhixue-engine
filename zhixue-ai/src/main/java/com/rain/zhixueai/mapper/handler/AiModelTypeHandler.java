package com.rain.zhixueai.mapper.handler;

import com.rain.zhixueai.enums.AiModelType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(AiModelType.class)
public class AiModelTypeHandler extends BaseTypeHandler<AiModelType> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, AiModelType parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getCode());
    }
    
    @Override
    public AiModelType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String code = rs.getString(columnName);
        return code == null ? null : AiModelType.fromCode(code);
    }
    
    @Override
    public AiModelType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String code = rs.getString(columnIndex);
        return code == null ? null : AiModelType.fromCode(code);
    }
    
    @Override
    public AiModelType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String code = cs.getString(columnIndex);
        return code == null ? null : AiModelType.fromCode(code);
    }
}
