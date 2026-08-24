package com.rain.zhixueadmin.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(
        basePackages = "com.rain.zhixueadmin.mapper",
        sqlSessionFactoryRef = "adminSqlSessionFactory",
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.REGEX,
                pattern = "com\\.rain\\.zhixueadmin\\.mapper\\.stats\\..*"
        )
)
@MapperScan(
        basePackages = "com.rain.zhixueadmin.mapper.stats.user",
        sqlSessionFactoryRef = "userSqlSessionFactory"
)
@MapperScan(
        basePackages = "com.rain.zhixueadmin.mapper.stats.problem",
        sqlSessionFactoryRef = "problemSqlSessionFactory"
)
@MapperScan(
        basePackages = "com.rain.zhixueadmin.mapper.stats.learning",
        sqlSessionFactoryRef = "learningSqlSessionFactory"
)
public class DataSourceConfig {

    @Primary
    @Bean(name = "adminDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.admin")
    public DataSource adminDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "userDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.user")
    public DataSource userDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "problemDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.problem")
    public DataSource problemDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "learningDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.learning")
    public DataSource learningDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "adminSqlSessionFactory")
    public SqlSessionFactory adminSqlSessionFactory(@Qualifier("adminDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        return bean.getObject();
    }

    @Bean(name = "userSqlSessionFactory")
    public SqlSessionFactory userSqlSessionFactory(@Qualifier("userDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/stats/UserStatsMapper.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        return bean.getObject();
    }

    @Bean(name = "problemSqlSessionFactory")
    public SqlSessionFactory problemSqlSessionFactory(@Qualifier("problemDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/stats/ProblemStatsMapper.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        return bean.getObject();
    }

    @Bean(name = "learningSqlSessionFactory")
    public SqlSessionFactory learningSqlSessionFactory(@Qualifier("learningDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/stats/LearningStatsMapper.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        return bean.getObject();
    }

    @Primary
    @Bean(name = "adminTransactionManager")
    public DataSourceTransactionManager adminTransactionManager(@Qualifier("adminDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "userTransactionManager")
    public DataSourceTransactionManager userTransactionManager(@Qualifier("userDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "problemTransactionManager")
    public DataSourceTransactionManager problemTransactionManager(@Qualifier("problemDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "learningTransactionManager")
    public DataSourceTransactionManager learningTransactionManager(@Qualifier("learningDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Primary
    @Bean(name = "adminSqlSessionTemplate")
    public SqlSessionTemplate adminSqlSessionTemplate(@Qualifier("adminSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "userSqlSessionTemplate")
    public SqlSessionTemplate userSqlSessionTemplate(@Qualifier("userSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "problemSqlSessionTemplate")
    public SqlSessionTemplate problemSqlSessionTemplate(@Qualifier("problemSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "learningSqlSessionTemplate")
    public SqlSessionTemplate learningSqlSessionTemplate(@Qualifier("learningSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
