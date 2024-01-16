package com.fast.loan.repository;

import com.fast.loan.domain.Repayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentRepository extends JpaRepository<Repayment, Long> {
}
