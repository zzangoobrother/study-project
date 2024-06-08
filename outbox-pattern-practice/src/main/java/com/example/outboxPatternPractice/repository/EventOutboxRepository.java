package com.example.outboxPatternPractice.repository;

import com.example.outboxPatternPractice.entity.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, Long> {
}
