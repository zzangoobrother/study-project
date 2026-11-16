package com.example.urlshortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "short_url")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_key")
    private String shortKey;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ShortUrl create(String originalUrl) {
        ShortUrl shortUrl = new ShortUrl();
        shortUrl.originalUrl = originalUrl;
        shortUrl.createdAt = LocalDateTime.now();
        return shortUrl;
    }

    // 키를 미리 정해 두고 INSERT 하는 방식(random)에서 사용한다
    public static ShortUrl create(String originalUrl, String shortKey) {
        ShortUrl shortUrl = create(originalUrl);
        shortUrl.shortKey = shortKey;
        return shortUrl;
    }

    public void assignShortKey(String shortKey) {
        this.shortKey = shortKey;
    }
}
