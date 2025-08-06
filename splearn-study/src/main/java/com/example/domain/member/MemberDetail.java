package com.example.domain.member;

import com.example.domain.AbstractEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class MemberDetail extends AbstractEntity {

    @Embedded
    private Profile profile;

    private String introduction;

    private LocalDateTime registerAt;

    private LocalDateTime activatedAt;

    private LocalDateTime deactivatedAt;


    static MemberDetail create() {
        MemberDetail memberDetail = new MemberDetail();
        memberDetail.registerAt = LocalDateTime.now();
        return memberDetail;
    }

    void activatedAt() {
        Assert.isTrue(activatedAt == null, "이미 activatedAt은 설정되었습니다.");
        this.activatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        Assert.isTrue(deactivatedAt == null, "이미 deactivatedAt은 설정되었습니다.");
        this.deactivatedAt = LocalDateTime.now();
    }

    void updateInfo(MemberInfoUpdateRequest updateRequest) {
        this.profile = new Profile(updateRequest.profileAddress());
        this.introduction = Objects.requireNonNull(updateRequest.introduction());
    }
}
