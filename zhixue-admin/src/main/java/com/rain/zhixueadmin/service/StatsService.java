package com.rain.zhixueadmin.service;

import java.util.Map;

public interface StatsService {
    
    Map<String, Object> getUserStats();
    
    Map<String, Object> getProblemStats();
    
    Map<String, Object> getLearningStats();
    
    Map<String, Object> getSystemStats();
    
    Map<String, Object> getAllStats();
}
