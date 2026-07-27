package com.subtracker.bot.dto;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper used across all controllers. Guarantees every endpoint returns a consistent shape:
 * success flag, HTTP status, a human-readable message, and the payload.
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final int status;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, int status, String message, T data) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, 200, message, data);
    }

    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return new ApiResponse<>(true, status, message, data);
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<>(false, status, message, null);
    }

    public static <T> ApiResponse<T> validationError(T errors) {
        return new ApiResponse<>(false, 400, "Validation failed", errors);
    }
}