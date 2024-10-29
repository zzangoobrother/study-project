package com.example.dto;

import java.util.Date;

public record UserDTO(
        int id,
        String userId,
        String password,
        String nickName,
        boolean isAdmin,
        Date createTime,
        boolean isWithDraw,
        Status status,
        Date updateTime
) {

    public void setCreateTime(Date date) {

    }

    public void setPassword(String s) {

    }

    public enum  Status {
        DEFAULT, ADMIN, DELETED
    }
}
