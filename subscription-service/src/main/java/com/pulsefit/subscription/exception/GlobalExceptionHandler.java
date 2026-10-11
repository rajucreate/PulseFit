package com.pulsefit.subscription.exception;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map<String, Object> missing(ResourceNotFoundException e) {
    return body(404, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, Object> invalid(MethodArgumentNotValidException e) {
    return body(400, "Invalid request data");
  }

  @ExceptionHandler(ConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  Map<String, Object> conflict(ConflictException e) {
    return body(409, e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  Map<String, Object> denied(AccessDeniedException e) {
    return body(403, e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, Object> illegalArgument(IllegalArgumentException e) {
    return body(400, e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, Object> unavailable(IllegalStateException e) {
    return body(503, e.getMessage());
  }

  private Map<String, Object> body(int status, String message) {
    return Map.of("timestamp", OffsetDateTime.now(), "status", status, "message", message);
  }
}
