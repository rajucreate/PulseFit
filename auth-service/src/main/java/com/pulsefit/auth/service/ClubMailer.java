package com.pulsefit.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClubMailer {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private final ObjectMapper json = new ObjectMapper();
  private final String baseUrl;

  public ClubMailer(@Value("${pulsefit.mailer.url:http://127.0.0.1:8090}") String baseUrl) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
  }

  public boolean configured() {
    try {
      HttpResponse<String> response = http.send(healthRequest(), HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200
          && response.body() != null
          && response.body().contains("\"configured\":true");
    } catch (Exception ex) {
      return false;
    }
  }

  public void send(String to, String subject, String text) {
    if (!configured()) {
      throw new IllegalStateException("SMTP is not configured");
    }
    try {
      String body = json.writeValueAsString(Map.of("to", to, "subject", subject, "text", text));
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(baseUrl + "/send"))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Mail could not be sent");
      }
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Mail could not be sent");
    }
  }

  private HttpRequest healthRequest() {
    return HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
        .timeout(Duration.ofSeconds(2))
        .GET()
        .build();
  }
}
