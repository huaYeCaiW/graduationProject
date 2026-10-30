package com.donghuan.common;

import org.springframework.stereotype.Component;

@Component
public class PointKeys {
    public String key (String deviceCode, String pointCode) {
        return deviceCode + ":" + pointCode;
    }
}
