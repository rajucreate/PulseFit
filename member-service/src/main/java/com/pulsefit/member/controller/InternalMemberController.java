package com.pulsefit.member.controller;

import com.pulsefit.member.dto.MemberRequest;
import com.pulsefit.member.dto.MemberResponse;
import com.pulsefit.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members/internal")
public class InternalMemberController {

  private final MemberService service;

  public InternalMemberController(MemberService service) {
    this.service = service;
  }

  @GetMapping("/by-email")
  public MemberResponse findByEmail(@RequestParam("email") String email) {
    return service.findByEmail(email);
  }

  @PostMapping("/create")
  @ResponseStatus(HttpStatus.CREATED)
  public MemberResponse createInternal(@Valid @RequestBody MemberRequest request) {
    return service.create(request);
  }
}

