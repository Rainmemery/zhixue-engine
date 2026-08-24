package com.rain.zhixueuser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.rain.zhixueuser", "com.rain.zhixuecommon"})
public class ZhixueUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhixueUserApplication.class, args);
    }

}
