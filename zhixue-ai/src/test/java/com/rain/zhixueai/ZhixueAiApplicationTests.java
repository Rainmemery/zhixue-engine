package com.rain.zhixueai;

import com.rain.zhixueai.test.MyTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ZhixueAiApplicationTests {

    @Test
    void contextLoads() {
        MyTest myTest = new MyTest();
        myTest.processOpenAIResponse("{\"id\":\"chat\",\"object\":\"chat.completion.chunk\",\"created\":1770476115,\"extend_fields\":{\"traceId\":\"212ba22917704761015304768e0d28\",\"requestId\":\"64bd35297ffe7b2b405981ba7f097136\"},\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"。它打破了\"}}],\"model\":\"glm-4.6\"}");
    }

}
