package com.rain.zhixueadmin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Autowired
    private SessionListener sessionListener;

    @Bean
    public ServletListenerRegistrationBean<SessionListener> sessionListenerRegistrationBean() {
        ServletListenerRegistrationBean<SessionListener> listenerRegBean = new ServletListenerRegistrationBean<>();
        listenerRegBean.setListener(sessionListener);
        return listenerRegBean;
    }
}
