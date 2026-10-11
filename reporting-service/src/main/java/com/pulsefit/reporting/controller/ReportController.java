package com.pulsefit.reporting.controller;

import com.pulsefit.reporting.service.ReportService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Long facilityId) {
    return service.dashboard(from, to, facilityId);
  }

  @GetMapping("/members")
  public Map<String, Object> members() { return service.memberReport(); }

  @GetMapping("/subscriptions")
  public Map<String, Object> subscriptions() { return service.subscriptionReport(); }

  @GetMapping("/attendance")
  public Map<String, Object> attendance(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Long facilityId) {
    return service.attendanceReport(from, to, facilityId);
  }

  @GetMapping("/revenue")
  public Map<String, Object> revenue(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.revenueReport(from, to);
  }

  @GetMapping("/payments")
  public Map<String, Object> payments(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.paymentReport(from, to);
  }

  @GetMapping("/facilities")
  public Map<String, Object> facilities() { return service.facilityReport(); }

  @GetMapping("/trainers")
  public Map<String, Object> trainers() { return service.trainerReport(); }
}
