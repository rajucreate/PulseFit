package com.pulsefit.member;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.pulsefit.member.dto.MemberRequest;
import com.pulsefit.member.exception.ResourceNotFoundException;
import com.pulsefit.member.model.Member;
import com.pulsefit.member.model.MemberStatus;
import com.pulsefit.member.repository.MemberRepository;
import com.pulsefit.member.service.MemberService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
  @Mock MemberRepository repository;
  @InjectMocks MemberService service;

  @Test
  void missingMemberIsRejected() {
    when(repository.findById(42L)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> service.findById(42L));
  }

  @Test
  void createMapsRequest() {
    Member saved = new Member("Ava", "ava@example.com", "555", null, MemberStatus.ACTIVE);
    when(repository.existsByEmail("ava@example.com")).thenReturn(false);
    when(repository.save(org.mockito.ArgumentMatchers.any(Member.class))).thenReturn(saved);
    service.create(new MemberRequest("Ava", "ava@example.com", "555", (LocalDate) null, MemberStatus.ACTIVE));
  }

  @Test
  void duplicateEmailOnCreateIsRejected() {
    when(repository.existsByEmail("duplicate@example.com")).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                new MemberRequest(
                    "Duplicate", "duplicate@example.com", "555", (LocalDate) null, MemberStatus.ACTIVE)));
  }

  @Test
  void duplicateEmailOnUpdateIsRejected() {
    Member member = new Member("Ava", "ava@example.com", "555", null, MemberStatus.ACTIVE);
    when(repository.findById(1L)).thenReturn(Optional.of(member));
    when(repository.existsByEmailAndIdNot("existing@example.com", 1L)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.update(
                1L,
                new MemberRequest(
                    "Ava Updated", "existing@example.com", "555", (LocalDate) null, MemberStatus.ACTIVE)));
  }
}
