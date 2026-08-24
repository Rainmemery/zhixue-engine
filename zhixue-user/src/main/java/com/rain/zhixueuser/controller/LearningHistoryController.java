package com.rain.zhixueuser.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixuecommon.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
public class LearningHistoryController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    private static final String LEARNING_SERVICE_URL = "http://zhixue-learning/api/v1/learning/records";

    @GetMapping("/{userId}/learning-history")
    public Result getLearningHistory(@PathVariable("userId") Long userId,
                                     @RequestHeader("Authorization") String authHeader,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer size,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate) {
        try {
            String jwtToken = authHeader.replaceFirst("^Bearer\\s+", "").trim();

            if (!jwtUtil.getUserIdFromToken(jwtToken).equals(userId.toString())) {
                log.info("权限验证失败: userId={}, tokenUserId={}", userId, jwtUtil.getUserIdFromToken(jwtToken));
                return Result.wrong("无权限");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);

            StringBuilder urlBuilder = new StringBuilder(LEARNING_SERVICE_URL);
            urlBuilder.append("?userId=").append(userId);
            urlBuilder.append("&page=").append(page);
            urlBuilder.append("&size=").append(size);

            if (status != null && !status.isEmpty()) {
                urlBuilder.append("&status=").append(status);
            }
            if (startDate != null && !startDate.isEmpty()) {
                urlBuilder.append("&startDate=").append(startDate);
            }
            if (endDate != null && !endDate.isEmpty()) {
                urlBuilder.append("&endDate=").append(endDate);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("调用learning服务: {}", urlBuilder.toString());
            ResponseEntity<Map> response = restTemplate.exchange(
                    urlBuilder.toString(),
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return Result.wrong("获取学习历史失败");
            }

            Integer code = (Integer) responseBody.get("code");
            if (code == null || code != 200) {
                String msg = (String) responseBody.get("msg");
                return Result.wrong(msg != null ? msg : "获取学习历史失败");
            }

            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data == null) {
                return Result.wrong("获取学习历史失败");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("items", data.get("items"));

            Map<String, Object> pagination = (Map<String, Object>) data.get("pagination");
            if (pagination != null) {
                result.put("total", pagination.get("total"));
                result.put("page", pagination.get("page"));
                result.put("size", pagination.get("size"));
            }

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取学习历史失败: {}", e.getMessage(), e);
            return Result.wrong("获取学习历史失败: " + e.getMessage());
        }
    }
}
