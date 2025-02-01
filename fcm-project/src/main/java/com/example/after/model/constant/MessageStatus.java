package com.example.after.model.constant;

public enum MessageStatus {
    WAITING("대기"),
    COMPLETED("완료"),
    CANCEL("취소")
    ;

    private final String valule;

    MessageStatus(String valule) {
        this.valule = valule;
    }
}

