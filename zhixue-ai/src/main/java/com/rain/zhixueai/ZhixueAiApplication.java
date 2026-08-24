package com.rain.zhixueai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.rain.zhixueai", "com.rain.zhixuecommon"})
public class ZhixueAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhixueAiApplication.class, args);
    }

}
