package com.company.hrms.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 哈希（身份证号检索用；生产环境建议加盐）。
 */
public final class Sha256HashUtil {

    private Sha256HashUtil() {
    }

    public static String hash(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String hashWithSalt(String raw, String salt) {
        return hash(salt + ":" + raw);
    }
}
