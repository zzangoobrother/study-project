package com.example.urlshortener;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KeyRangeRepository extends JpaRepository<KeyRange, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KeyRange k where k.name = :name")
    Optional<KeyRange> findForUpdate(@Param("name") String name);
}
