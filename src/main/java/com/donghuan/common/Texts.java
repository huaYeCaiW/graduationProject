package com.donghuan.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Texts {

    private Texts() {
    }

    public static String require(String raw, String field, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 不能为空");
        }
        String value = raw.trim();
        if (value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " 不能超过 " + maxLength + " 字");
        }
        return value;
    }

    public static String trimToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}