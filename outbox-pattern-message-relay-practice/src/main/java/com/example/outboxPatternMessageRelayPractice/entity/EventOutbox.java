package com.example.outboxPatternMessageRelayPractice.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_out_box")
@Entity
public class EventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_load", columnDefinition = "BINARY(16)")
    private UUID payLoad;

    public EventOutbox(UUID payLoad) {
        this.payLoad = payLoad;
    }
}
