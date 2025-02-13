package com.example.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "options", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> option;

    public Message(String title, String content, Map<String, String> option) {
        this.title = title;
        this.content = content;
        this.option = option;
    }
}
