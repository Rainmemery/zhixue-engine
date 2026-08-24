package com.rain.zhixueai.scheduling.failover;

public interface FailoverEventListener {
    
    void onFailoverEvent(FailoverEvent event);
}
