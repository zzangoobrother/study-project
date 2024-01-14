package com.fast.loan.repository;

import com.fast.loan.domain.Judgment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JudgmentRepository extends JpaRepository<Judgment, Long> {
}
