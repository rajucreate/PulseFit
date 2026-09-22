package com.pulsefit.auth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MemberDtoTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void shouldDeserializeMemberDtoWithIsoOffsetDateTime() throws Exception {
    String json = """
        {
          "id": 1,
          "name": "Jane Doe",
          "email": "jane.doe@pulsefit.com",
          "contact": "555-0199",
          "dateOfBirth": "1990-01-01",
          "status": "ACTIVE",
          "createdAt": "2026-09-21T09:52:26.123456+05:30"
        }
        """;

    MemberDto memberDto = objectMapper.readValue(json, MemberDto.class);

    assertNotNull(memberDto);
    assertEquals(1L, memberDto.id());
    assertEquals("Jane Doe", memberDto.name());
    assertEquals("jane.doe@pulsefit.com", memberDto.email());
    assertEquals("555-0199", memberDto.contact());
    assertEquals(LocalDate.of(1990, 1, 1), memberDto.dateOfBirth());
    assertEquals("ACTIVE", memberDto.status());
    assertNotNull(memberDto.createdAt());
    assertTrue(
        OffsetDateTime.parse("2026-09-21T09:52:26.123456+05:30").isEqual(memberDto.createdAt()));
  }
}
