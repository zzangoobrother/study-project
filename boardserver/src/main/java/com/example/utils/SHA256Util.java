package com.example.utils;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;

@Slf4j
public class SHA256Util {
    public static final String ENCRYPTION_KEY = "SHA-256";
    public static String encryptSHA256(String str) {
        String SHA = null;

        MessageDigest sh;
        try {
            sh = MessageDigest.getInstance(ENCRYPTION_KEY);
            sh.update(str.getBytes());
            byte[] bytes = sh.digest();
            StringBuffer sb = new StringBuffer();
            for (byte byteDatum : bytes) {
                sb.append(Integer.toString((byteDatum & 0xff) + 0x100, 16).substring(1));
            }

            SHA = sb.toString();
        } catch (Exception e) {
            log.error("encryptSHA256 ERROR : {}", e.getMessage());
            SHA = null;
        }

        return SHA;
    }
}
