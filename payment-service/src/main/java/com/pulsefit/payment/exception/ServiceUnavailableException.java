package com.pulsefit.payment.exception;

public class ServiceUnavailableException extends RuntimeException {
  public ServiceUnavailableException(String message) { super(message); }
}
