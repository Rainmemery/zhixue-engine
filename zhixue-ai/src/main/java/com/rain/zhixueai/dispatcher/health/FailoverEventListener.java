package com.rain.zhixueai.dispatcher.health;

public interface FailoverEventListener {
    
    void onFailoverEvent(FailoverEvent event);
}
