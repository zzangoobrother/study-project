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
public class Judgment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long judgmentId;

    @Column(columnDefinition = "bigint NOT NULL COMMENT '신청 ID'")
    private Long applicationId;

    @Column(columnDefinition = "varchar(12) NOT NULL COMMENT '심사자'")
    private String name;

    @Column(columnDefinition = "decimal(15,2) DEFAULT NULL COMMENT '승인 금액'")
    private BigDecimal approvalAmount;
}
