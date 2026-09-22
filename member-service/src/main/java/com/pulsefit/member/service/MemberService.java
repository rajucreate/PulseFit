package com.pulsefit.member.service;

import com.pulsefit.member.dto.*;
import com.pulsefit.member.exception.ResourceNotFoundException;
import com.pulsefit.member.model.Member;
import com.pulsefit.member.repository.MemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemberService {
  private final MemberRepository repository;

  public MemberService(MemberRepository repository) {
    this.repository = repository;
  }

  public MemberResponse create(MemberRequest request) {
    if (repository.existsByEmail(request.email())) {
      throw new IllegalArgumentException("Email already in use: " + request.email());
    }
    return response(
        repository.save(
            new Member(
                request.name(),
                request.email(),
                request.contact(),
                request.dateOfBirth(),
                request.status())));
  }

  public List<MemberResponse> findAll() {
    return repository.findAll().stream().map(this::response).toList();
  }

  public MemberResponse findById(Long id) {
    return response(
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id)));
  }

  public MemberResponse findByEmail(String email) {
    return response(
        repository
            .findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found with email: " + email)));
  }

  public MemberResponse update(Long id, MemberRequest request) {
    Member member =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));
    if (repository.existsByEmailAndIdNot(request.email(), id)) {
      throw new IllegalArgumentException("Email already in use: " + request.email());
    }
    member.update(
        request.name(),
        request.email(),
        request.contact(),
        request.dateOfBirth(),
        request.status());
    return response(repository.save(member));
  }

  public void delete(Long id) {
    if (!repository.existsById(id)) throw new ResourceNotFoundException("Member not found: " + id);
    repository.deleteById(id);
  }

  private MemberResponse response(Member m) {
    return new MemberResponse(
        m.getId(),
        m.getName(),
        m.getEmail(),
        m.getContact(),
        m.getDateOfBirth(),
        m.getStatus(),
        m.getCreatedAt());
  }
}
