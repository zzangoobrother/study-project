package com.example.outboxPatternPractice.entity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
@Entity
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", columnDefinition = "BINARY(16)")
    private UUID code;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    public Order(UUID code, Long userId, LocalDateTime createAt) {
        this.code = code;
        this.userId = userId;
        this.createAt = createAt;
    }
}
