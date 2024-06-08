package com.example.outboxPatternPractice.entity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
@Entity
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    public Order(String code, LocalDateTime createAt) {
        this.code = code;
        this.createAt = createAt;
    }
}
