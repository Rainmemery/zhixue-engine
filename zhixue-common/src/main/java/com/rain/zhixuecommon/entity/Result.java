package com.rain.zhixuecommon.entity;

import lombok.Data;

@Data
public class Result {
    Integer code;
    String msg;
    Object data;
    Long timestamp;
    String requestId;

    /**
     {
     "code": 200,
     "message": "success",
     "data": {},
     "timestamp": 1735228800000,
     "requestId": "req_123456"
     }
     */
    public static Result success(Object data){
        Result result = new Result();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        result.setRequestId(null);//暂时置空
        return result;
    }

    public static Result success(Object data,String message){
        Result result = new Result();
        result.setCode(200);
        result.setMsg(message);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        result.setRequestId(null);//暂时置空
        return result;
    }

    public static Result success(Object data,String message,String requestId){
        Result result = new Result();
        result.setCode(200);
        result.setMsg(message);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        result.setRequestId(requestId);
        return result;
    }

    public static Result success(Integer code,Object data,String message,String requestId){
        Result result = new Result();
        result.setCode(code);
        result.setMsg(message);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        result.setRequestId(requestId);
        return result;
    }
    /**
     {
     "code": 400,
     "message": "参数验证失败",
     "errors": [
     {
     "field": "username",
     "message": "用户名不能为空"
     }
     ],
     "timestamp": 1735228800000,
     "requestId": "req_123456"
     }
     */
    public static Result wrong(String message){
        Result result = new Result();
        result.setCode(400);
        result.setMsg(message);
        result.setData(null);
        result.setTimestamp(System.currentTimeMillis());
        result.setRequestId(null);//暂时置空
        return result;
    }

    public static Result wrong(String message,String requestId){
        Result result = new Result();
        result.setCode(400);
        result.setMsg(message);
        result.setData(null);
        result.setTimestamp(System.currentTimeMillis());
        result.setRequestId(requestId);//暂时置空
        return result;
    }

}
