package com.pulsefit.member.controller;

import com.pulsefit.member.dto.MemberRequest;
import com.pulsefit.member.dto.MemberResponse;
import com.pulsefit.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

  @GetMapping("/summaries")
  public java.util.List<MemberResponse> summaries() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  public MemberResponse findById(@PathVariable Long id) {
    return service.findById(id);
  }

  @PostMapping("/create")
  @ResponseStatus(HttpStatus.CREATED)
  public MemberResponse createInternal(@Valid @RequestBody MemberRequest request) {
    return service.create(request);
  }
}

