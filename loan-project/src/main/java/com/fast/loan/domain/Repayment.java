package com.fast.loan.domain;

import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@Where(clause = "is_deleted=false")
@Entity
public class Repayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long repaymentId;

    @Column(columnDefinition = "bigint NOT NULL COMMENT '신청 ID'")
    private Long applicationId;

    @Column(columnDefinition = "decimal(15,2) DEFAULT NULL COMMENT '상환 금액'")
    private BigDecimal repaymentAmount;
}
