package com.pulsefit.trainer;

import com.pulsefit.trainer.client.RemoteClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {RemoteClients.FacilityClient.class, RemoteClients.MemberClient.class})
public class TrainerServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(TrainerServiceApplication.class, args);
  }
}
