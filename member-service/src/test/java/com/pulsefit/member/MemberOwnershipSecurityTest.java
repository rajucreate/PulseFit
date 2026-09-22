package com.pulsefit.member;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pulsefit.member.controller.MemberController;
import com.pulsefit.member.dto.MemberRequest;
import com.pulsefit.member.dto.MemberResponse;
import com.pulsefit.member.model.Member;
import com.pulsefit.member.model.MemberStatus;
import com.pulsefit.member.repository.MemberRepository;
import com.pulsefit.member.security.AuthenticatedUser;
import com.pulsefit.member.service.MemberService;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class MemberOwnershipSecurityTest {

  @Mock private MemberRepository memberRepository;
  private MemberService memberService;
  private MemberController memberController;

  @BeforeEach
  void setUp() {
    memberService = new MemberService(memberRepository);
    memberController = new MemberController(memberService);
  }

  @Test
  void memberAccessingOwnProfile_Succeeds() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alice@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    Member member = new Member("Alice", "alice@pulsefit.com", "555", null, MemberStatus.ACTIVE);
    org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 42L);
    when(memberRepository.findById(42L)).thenReturn(Optional.of(member));

    MemberResponse response = memberController.findById(42L, auth);
    assertNotNull(response);
    assertEquals(42L, response.id());
  }

  @Test
  void memberAccessingAnotherMemberProfile_ThrowsAccessDenied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alice@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    assertThrows(AccessDeniedException.class, () -> memberController.findById(99L, auth));
    verify(memberRepository, never()).findById(anyLong());
  }

  @Test
  void staffAccessingAnyMemberProfile_Succeeds() {
    AuthenticatedUser staff = new AuthenticatedUser(2L, "staff@pulsefit.com", "STAFF", null);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            staff, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_STAFF")));

    Member member = new Member("Bob", "bob@pulsefit.com", "555", null, MemberStatus.ACTIVE);
    org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 99L);
    when(memberRepository.findById(99L)).thenReturn(Optional.of(member));

    MemberResponse response = memberController.findById(99L, auth);
    assertNotNull(response);
    assertEquals(99L, response.id());
  }

  @Test
  void memberUpdatingAnotherMemberProfile_ThrowsAccessDenied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alice@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    MemberRequest req = new MemberRequest("Bob", "bob@pulsefit.com", "555", (java.time.LocalDate) null, MemberStatus.ACTIVE);
    assertThrows(AccessDeniedException.class, () -> memberController.update(99L, req, auth));
    verify(memberRepository, never()).findById(anyLong());
  }
}
