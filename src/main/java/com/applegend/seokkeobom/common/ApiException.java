package com.applegend.seokkeobom.common;

import org.springframework.http.HttpStatus;

/** 화면에 그대로 보여 줄 수 있는 한국어 메시지와, 화면이 분기할 수 있는 코드를 함께 담는 예외. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
