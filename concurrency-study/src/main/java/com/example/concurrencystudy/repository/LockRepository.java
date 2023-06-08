package com.example.concurrencystudy.repository;

import com.example.concurrencystudy.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LockRepository extends JpaRepository<Stock, Long> { // 실제 운영에서 사용하면 다른 db 서버에서 락을 잡아야 한다. 따라서 stock가 아닌 다른 객체가 들어간다.

    @Query(value = "select get_lock(:key, 3000)", nativeQuery = true)
    void getLock(String key);

    @Query(value = "select release_lock(:key)", nativeQuery = true)
    void releaseLock(String key);
}
