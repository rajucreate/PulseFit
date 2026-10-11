package com.pulsefit.reporting;

import com.pulsefit.reporting.client.ReportClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(
    clients = {
      ReportClients.MemberClient.class,
      ReportClients.SubscriptionClient.class,
      ReportClients.AttendanceClient.class,
      ReportClients.PaymentClient.class,
      ReportClients.FacilityClient.class,
      ReportClients.TrainerClient.class
    })
public class ReportingServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ReportingServiceApplication.class, args);
  }
}
