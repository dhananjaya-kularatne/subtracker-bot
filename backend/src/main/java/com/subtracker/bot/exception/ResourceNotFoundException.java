package com.subtracker.bot.exception;

/**
 * Thrown when a requested resource (e.g. a Subscription) cannot be found — either it doesn't exist, or it exists but doesn't belong to the requesting user.
 * Caught by GlobalExceptionHandler and converted into a 404 ApiResponse.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}