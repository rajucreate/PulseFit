package com.pulsefit.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.pulsefit.payment.client.Clients;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(
    clients = {
      Clients.SubscriptionClient.class,
      Clients.PlanClient.class,
      Clients.MemberClient.class,
      Clients.NotificationClient.class
    })
public class PaymentServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PaymentServiceApplication.class, args);
  }
}
