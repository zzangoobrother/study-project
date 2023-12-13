package com.practice.storage.db.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = {"com.practice.storage.db"})
@EnableJpaRepositories(basePackages = {"com.practice.storage.db"})
public class MainJpaConfig {
}
