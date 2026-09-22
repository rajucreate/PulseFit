package com.pulsefit.member.repository;

import com.pulsefit.member.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
  boolean existsByEmail(String email);

  boolean existsByEmailAndIdNot(String email, Long id);

  java.util.Optional<Member> findByEmail(String email);
}
