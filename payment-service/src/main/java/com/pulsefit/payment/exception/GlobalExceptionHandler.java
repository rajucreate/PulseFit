package com.pulsefit.payment.exception;

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
  Map<String, Object> notFound(ResourceNotFoundException ex) { return body(404, ex.getMessage()); }

  @ExceptionHandler(ConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  Map<String, Object> conflict(ConflictException ex) { return body(409, ex.getMessage()); }

  @ExceptionHandler(ServiceUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, Object> unavailable(ServiceUnavailableException ex) { return body(503, ex.getMessage()); }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  Map<String, Object> denied(AccessDeniedException ex) { return body(403, ex.getMessage()); }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, Object> bad(Exception ex) {
    String message = ex instanceof MethodArgumentNotValidException ? "Invalid request data" : ex.getMessage();
    return body(400, message);
  }

  private Map<String, Object> body(int status, String message) {
    return Map.of("timestamp", OffsetDateTime.now(), "status", status, "message", message);
  }
}
