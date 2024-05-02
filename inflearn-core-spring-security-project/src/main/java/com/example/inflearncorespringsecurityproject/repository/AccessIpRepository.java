package com.example.inflearncorespringsecurityproject.repository;

import com.example.inflearncorespringsecurityproject.domain.entity.AccessIp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessIpRepository extends JpaRepository<AccessIp, Long> {

    AccessIp findByIpAddress(String ipAddress);
}
