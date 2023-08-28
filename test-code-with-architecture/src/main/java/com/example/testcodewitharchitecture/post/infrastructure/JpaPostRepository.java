package com.example.testcodewitharchitecture.post.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPostRepository extends JpaRepository<PostEntity, Long> {
}
