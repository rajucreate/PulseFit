package com.pulsefit.auth.controller;

import com.pulsefit.auth.dto.AuditLogResponse;
import com.pulsefit.auth.service.AuditLogService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

  private final AuditLogService auditLogService;

  public AuditController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  public List<AuditLogResponse> getAllLogs() {
    return auditLogService.getAllLogs();
  }
}

