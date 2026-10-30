package com.pulsefit.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayErrorConfig {

  @Bean
  @Order(-2)
  public ErrorWebExceptionHandler gatewayJsonErrorHandler(ObjectMapper objectMapper) {
    return (ServerWebExchange exchange, Throwable ex) -> {
      if (exchange.getResponse().isCommitted()) {
        return Mono.error(ex);
      }
      HttpStatus status = resolveStatus(ex);
      String code =
          status == HttpStatus.SERVICE_UNAVAILABLE
              ? "SERVICE_UNAVAILABLE"
              : status == HttpStatus.NOT_FOUND ? "ROUTE_NOT_FOUND" : "GATEWAY_ERROR";
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("timestamp", OffsetDateTime.now().toString());
      body.put("status", status.value());
      body.put("error", status.getReasonPhrase());
      body.put("code", code);
      body.put("message", safeMessage(ex, status));
      body.put("path", exchange.getRequest().getURI().getPath());
      byte[] bytes;
      try {
        bytes = objectMapper.writeValueAsBytes(body);
      } catch (JsonProcessingException jsonEx) {
        bytes =
            ("{\"status\":"
                    + status.value()
                    + ",\"message\":\"Gateway error\"}")
                .getBytes(StandardCharsets.UTF_8);
      }
      exchange.getResponse().setStatusCode(status);
      exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    };
  }

  private static HttpStatus resolveStatus(Throwable ex) {
    if (ex instanceof NotFoundException) {
      return HttpStatus.SERVICE_UNAVAILABLE;
    }
    if (ex instanceof ResponseStatusException responseStatusException
        && responseStatusException.getStatusCode() instanceof HttpStatus httpStatus) {
      return httpStatus;
    }
    String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
    if (message.contains("connection refused")
        || message.contains("unable to find instance")
        || message.contains("no servers available")
        || message.contains("service unavailable")) {
      return HttpStatus.SERVICE_UNAVAILABLE;
    }
    return HttpStatus.BAD_GATEWAY;
  }

  private static String safeMessage(Throwable ex, HttpStatus status) {
    if (status == HttpStatus.SERVICE_UNAVAILABLE) {
      return "The requested microservice is unavailable. Check Eureka registration and try again.";
    }
    String message = ex.getMessage();
    return message == null || message.isBlank() ? status.getReasonPhrase() : message;
  }
}
