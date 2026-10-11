package com.pulsefit.reporting;

import static org.junit.jupiter.api.Assertions.*;

import com.pulsefit.reporting.client.ReportClients.PaymentView;
import com.pulsefit.reporting.service.ReportMath;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportMathTest {
  @Test
  void revenueCountsSuccessfulPaymentsOnceAndIgnoresFailuresAndRefunds() {
    OffsetDateTime today = OffsetDateTime.now();
    List<PaymentView> rows =
        List.of(
            new PaymentView(1L, new BigDecimal("20.00"), "sim-1", "SUCCESS", today),
            new PaymentView(2L, new BigDecimal("20.00"), "sim-1", "SUCCESS", today),
            new PaymentView(3L, new BigDecimal("15.00"), "sim-2", "FAILED", today),
            new PaymentView(4L, new BigDecimal("30.00"), "sim-3", "REFUNDED", today),
            new PaymentView(5L, new BigDecimal("5.00"), "sim-4", "SUCCESS", today.minusDays(40)));
    Map<String, Object> report = ReportMath.revenue(rows, null, null);
    assertEquals(new BigDecimal("25.00"), report.get("total"));
    assertEquals(1L, report.get("failedPayments"));
    assertEquals(1L, report.get("refundedPayments"));
    assertEquals(2L, report.get("successfulPayments"));
  }

  @Test
  void unavailableIsDistinctFromZeroInTheServiceContract() {
    Map<String, Object> empty = ReportMath.members(List.of());
    assertEquals(0, empty.get("total"));
    assertFalse(empty.containsKey("available"));
  }
}
