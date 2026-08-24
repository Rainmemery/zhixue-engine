package com.rain.zhixueadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.rain.zhixueadmin", "com.rain.zhixuecommon"})
public class ZhixueAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhixueAdminApplication.class, args);
    }

}
