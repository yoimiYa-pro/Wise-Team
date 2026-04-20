package com.teampm.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务层抛出的可预期错误，由 {@link com.teampm.exception.GlobalExceptionHandler} 映射为对应状态码与 JSON。
 */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
