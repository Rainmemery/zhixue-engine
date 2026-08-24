package com.rain.zhixueproblem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rain.zhixueproblem", "com.rain.zhixuecommon"})
@MapperScan("com.rain.zhixueproblem.mapper")
public class ZhixueProblemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhixueProblemApplication.class, args);
    }

}
