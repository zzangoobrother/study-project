package com.example.outboxPatternMessageRelayPractice.repository;

import com.example.outboxPatternMessageRelayPractice.entity.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, Long> {
}
