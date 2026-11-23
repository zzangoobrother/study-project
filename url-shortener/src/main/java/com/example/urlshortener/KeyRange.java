package com.example.urlshortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "key_range")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeyRange {

    // 단축 URL 번호를 나눠 주는 행의 이름
    static final String SHORT_URL = "short_url";

    @Id
    private String name;

    @Column(name = "next_start", nullable = false)
    private long nextStart;

    void advanceTo(long nextStart) {
        this.nextStart = nextStart;
    }
}
