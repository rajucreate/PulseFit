package com.pulsefit.payment.razorpay;

public interface RazorpayGateway {
  boolean configured();

  String keyId();

  RazorpayOrder createOrder(int amountPaise, String receipt);

  boolean signatureValid(String orderId, String paymentId, String signature);

  RazorpayPayment fetch(String paymentId);

  record RazorpayOrder(String id, int amountPaise, String status) {}

  record RazorpayPayment(String id, String orderId, int amountPaise, String status) {}
}
