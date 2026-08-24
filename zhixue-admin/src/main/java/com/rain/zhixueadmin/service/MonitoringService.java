package com.rain.zhixueadmin.service;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public interface MonitoringService {
    Object getSystemDetail();
}
