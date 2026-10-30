package com.pulsefit.gateway.controller;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class ServiceStatusController {
  private static final Set<String> EXPECTED =
      Set.of(
          "API-GATEWAY",
          "AUTH-SERVICE",
          "MEMBER-SERVICE",
          "SUBSCRIPTION-SERVICE",
          "ATTENDANCE-SERVICE",
          "FACILITY-SERVICE",
          "PAYMENT-SERVICE",
          "NOTIFICATION-SERVICE",
          "TRAINER-SERVICE",
          "REPORTING-SERVICE");

  private final DiscoveryClient discovery;

  public ServiceStatusController(DiscoveryClient discovery) {
    this.discovery = discovery;
  }

  @GetMapping("/status")
  public StatusResponse status() {
    List<ServiceView> services =
        EXPECTED.stream()
            .sorted()
            .map(this::view)
            .toList();
    long up = services.stream().filter(ServiceView::available).count();
    return new StatusResponse(
        OffsetDateTime.now(),
        services.size(),
        up,
        services.size() - up,
        services);
  }

  @GetMapping("/registry")
  public RegistryResponse registry() {
    List<RegisteredApp> apps =
        discovery.getServices().stream()
            .sorted(Comparator.naturalOrder())
            .map(
                name ->
                    new RegisteredApp(
                        name.toUpperCase(Locale.ROOT),
                        discovery.getInstances(name).stream().map(this::instance).toList()))
            .toList();
    return new RegistryResponse(OffsetDateTime.now(), apps.size(), apps);
  }

  private ServiceView view(String serviceId) {
    List<ServiceInstance> instances = discovery.getInstances(serviceId);
    if (instances == null || instances.isEmpty()) {
      // Eureka may register with different casing depending on environment.
      instances =
          discovery.getServices().stream()
              .filter(name -> name.equalsIgnoreCase(serviceId))
              .findFirst()
              .map(discovery::getInstances)
              .orElse(List.of());
    }
    boolean available = instances != null && !instances.isEmpty();
    return new ServiceView(
        serviceId,
        available,
        available ? "UP" : "DOWN",
        available ? instances.size() : 0,
        available
            ? instances.stream().map(this::instance).toList()
            : List.of());
  }

  private InstanceView instance(ServiceInstance instance) {
    return new InstanceView(
        instance.getInstanceId(),
        instance.getHost(),
        instance.getPort(),
        instance.getUri() == null ? null : instance.getUri().toString());
  }

  public record StatusResponse(
      OffsetDateTime checkedAt,
      long expectedServices,
      long availableServices,
      long unavailableServices,
      List<ServiceView> services) {}

  public record ServiceView(
      String serviceId,
      boolean available,
      String status,
      int instanceCount,
      List<InstanceView> instances) {}

  public record InstanceView(String instanceId, String host, int port, String uri) {}

  public record RegistryResponse(OffsetDateTime checkedAt, int appCount, List<RegisteredApp> applications) {}

  public record RegisteredApp(String name, List<InstanceView> instances) {}
}
