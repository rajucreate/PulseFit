package com.pulsefit.member.dto;

import com.pulsefit.member.model.MemberStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MemberRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank String contact,
    LocalDate dateOfBirth,
    @NotNull MemberStatus status) {}
