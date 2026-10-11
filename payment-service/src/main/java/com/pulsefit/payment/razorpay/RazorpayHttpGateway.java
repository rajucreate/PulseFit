package com.pulsefit.payment.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pulsefit.payment.exception.ServiceUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RazorpayHttpGateway implements RazorpayGateway {
  private final String keyId;
  private final String keySecret;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public RazorpayHttpGateway(
      @Value("${pulsefit.razorpay.key-id:}") String keyId,
      @Value("${pulsefit.razorpay.key-secret:}") String keySecret) {
    this.keyId = keyId == null ? "" : keyId.trim();
    this.keySecret = keySecret == null ? "" : keySecret.trim();
  }

  @Override
  public boolean configured() {
    return !keyId.isBlank() && !keySecret.isBlank();
  }

  @Override
  public String keyId() {
    return keyId;
  }

  @Override
  public RazorpayOrder createOrder(int amountPaise, String receipt) {
    ObjectNode body = json.createObjectNode();
    body.put("amount", amountPaise);
    body.put("currency", "INR");
    body.put("receipt", receipt);
    JsonNode created = post("/v1/orders", body);
    return new RazorpayOrder(created.path("id").asText(), created.path("amount").asInt(), created.path("status").asText());
  }

  @Override
  public boolean signatureValid(String orderId, String paymentId, String signature) {
    if (!configured() || signature == null || signature.isBlank()) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte value : raw) {
        hex.append(String.format("%02x", value));
      }
      return hex.toString().equalsIgnoreCase(signature.trim());
    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public RazorpayPayment fetch(String paymentId) {
    JsonNode body = get("/v1/payments/" + paymentId);
    return new RazorpayPayment(
        body.path("id").asText(),
        body.path("order_id").asText(),
        body.path("amount").asInt(),
        body.path("status").asText());
  }

  private JsonNode post(String path, JsonNode body) {
    try {
      HttpRequest request =
          authorized(path)
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .header("Content-Type", "application/json")
              .build();
      return read(http.send(request, HttpResponse.BodyHandlers.ofString()));
    } catch (ServiceUnavailableException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ServiceUnavailableException("Razorpay test API could not be reached");
    }
  }

  private JsonNode get(String path) {
    try {
      HttpRequest request = authorized(path).GET().build();
      return read(http.send(request, HttpResponse.BodyHandlers.ofString()));
    } catch (ServiceUnavailableException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ServiceUnavailableException("Razorpay test API could not be reached");
    }
  }

  private HttpRequest.Builder authorized(String path) {
    if (!configured()) {
      throw new ServiceUnavailableException(missingKeys());
    }
    String token =
        Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
    return HttpRequest.newBuilder(URI.create("https://api.razorpay.com" + path))
        .timeout(Duration.ofSeconds(8))
        .header("Authorization", "Basic " + token);
  }

  private JsonNode read(HttpResponse<String> response) throws Exception {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new ServiceUnavailableException("Razorpay test API rejected the request");
    }
    return json.readTree(response.body());
  }

  public static String missingKeys() {
    return "Razorpay test mode is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.";
  }
}
