package com.pulsefit.member.exception;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map<String, Object> notFound(ResourceNotFoundException ex) {
    return body(404, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, Object> invalid(MethodArgumentNotValidException ex) {
    return body(400, "Invalid request data");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, Object> illegalArgument(IllegalArgumentException ex) {
    return body(400, ex.getMessage());
  }

  private Map<String, Object> body(int status, String message) {
    return Map.of("timestamp", OffsetDateTime.now(), "status", status, "message", message);
  }
}
