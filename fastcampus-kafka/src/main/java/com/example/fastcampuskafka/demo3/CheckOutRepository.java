package com.example.fastcampuskafka.demo3;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckOutRepository extends JpaRepository<CheckOutEntity, Long> {
}
