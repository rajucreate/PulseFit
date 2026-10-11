package com.pulsefit.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.pulsefit.payment.razorpay.RazorpayHttpGateway;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RazorpaySignatureTest {
  @Test
  void acceptsTheOfficialOrderPaymentSignature() throws Exception {
    RazorpayHttpGateway gateway = new RazorpayHttpGateway("rzp_test_demo", "test_secret");
    String signature = hmac("test_secret", "order_1|pay_1");
    assertTrue(gateway.signatureValid("order_1", "pay_1", signature));
    assertFalse(gateway.signatureValid("order_1", "pay_1", "not-the-signature"));
    assertFalse(new RazorpayHttpGateway("", "").configured());
  }

  private static String hmac(String secret, String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    StringBuilder hex = new StringBuilder();
    for (byte value : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) {
      hex.append(String.format("%02x", value));
    }
    return hex.toString();
  }
}
