package com.example.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserConnectionId implements Serializable {

    private Long partnerAUserId;
    private Long partnerBUserId;

    public UserConnectionId() {}

    public UserConnectionId(Long partnerAUserId, Long partnerBUserId) {
        this.partnerAUserId = partnerAUserId;
        this.partnerBUserId = partnerBUserId;
    }

    public Long getPartnerAUserId() {
        return partnerAUserId;
    }

    public Long getPartnerBUserId() {
        return partnerBUserId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        UserConnectionId that = (UserConnectionId) object;
        return Objects.equals(partnerAUserId, that.partnerAUserId) && Objects.equals(partnerBUserId, that.partnerBUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partnerAUserId, partnerBUserId);
    }

    @Override
    public String toString() {
        return "UserConnectionId{" +
                "partnerAUserId=" + partnerAUserId +
                ", partnerBUserId=" + partnerBUserId +
                '}';
    }
}
