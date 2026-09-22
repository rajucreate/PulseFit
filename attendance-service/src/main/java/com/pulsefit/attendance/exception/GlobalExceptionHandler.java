package com.pulsefit.attendance.exception;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
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

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, Object> unavailable(IllegalStateException e) {
    return body(503, e.getMessage());
  }

  private Map<String, Object> body(int status, String message) {
    return Map.of("timestamp", OffsetDateTime.now(), "status", status, "message", message);
  }
}
