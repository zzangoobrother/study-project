package com.example.dbconnectiontest.repository;

import com.example.dbconnectiontest.model.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query(value = "SELECT m FROM Member m WHERE m.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Member getLock(@Param("id") Long id);
}
