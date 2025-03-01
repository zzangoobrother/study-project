package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SnowflakeController {

    private final Snowflake snowflake;

    public SnowflakeController(Snowflake snowflake) {
        this.snowflake = snowflake;
    }

    @GetMapping("/api/snowflake")
    public Long create() {
        return snowflake.nextId();
    }
}
