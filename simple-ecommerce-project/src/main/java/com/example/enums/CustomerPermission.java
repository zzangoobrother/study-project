package com.example.enums;

public enum CustomerPermission {
    GENERAL("일반"),
    BIZ("법인");

    private String permissionDesc;

    CustomerPermission(String permissionDesc) {
        this.permissionDesc = permissionDesc;
    }
}
