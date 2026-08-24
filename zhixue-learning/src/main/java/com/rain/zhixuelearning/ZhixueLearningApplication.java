package com.rain.zhixuelearning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.rain.zhixuelearning", "com.rain.zhixuecommon"})
public class ZhixueLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhixueLearningApplication.class, args);
    }

}
