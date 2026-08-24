package com.rain.zhixueadmin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SessionListener implements jakarta.servlet.http.HttpSessionListener {

    private static final Logger log = LoggerFactory.getLogger(SessionListener.class);

    private final AtomicInteger activeSessions = new AtomicInteger(0);

    @Override
    public void sessionCreated(jakarta.servlet.http.HttpSessionEvent se) {
        int count = activeSessions.incrementAndGet();
        log.debug("Session created, active sessions: {}", count);
    }

    @Override
    public void sessionDestroyed(jakarta.servlet.http.HttpSessionEvent se) {
        int count = activeSessions.decrementAndGet();
        log.debug("Session destroyed, active sessions: {}", count);
    }

    public int getActiveSessionsCount() {
        return activeSessions.get();
    }
}
