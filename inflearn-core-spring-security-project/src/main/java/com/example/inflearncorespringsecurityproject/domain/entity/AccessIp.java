package com.example.inflearncorespringsecurityproject.domain.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.io.Serializable;

@Getter
@NoArgsConstructor
@Entity
public class AccessIp implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "IP_ID")
    private Long id;

    @Column(name = "IP_ADDRESS", nullable = false)
    private String ipAddress;

    @Builder
    public AccessIp(Long id, String ipAddress) {
        this.id = id;
        this.ipAddress = ipAddress;
    }
}
