package com.donghuan.model;

import java.util.Locale;

public enum Protocol {
    MQTT,
    MODBUS;

    public static Protocol from(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
