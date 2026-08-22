package com.aischool.server.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 统一 REST 返回体 */
@Data
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;       // 0 = 成功
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
