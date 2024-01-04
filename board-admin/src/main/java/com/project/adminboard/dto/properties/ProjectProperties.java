package com.project.adminboard.dto.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("project")
public record ProjectProperties(
        Board board
) {

    public record Board(String url) {}
}
