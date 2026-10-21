package com.donghuan.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Component
@ConfigurationProperties(prefix = "donghuan")
public class DeviceProperties {

    private List<DeviceConfig> devices = new ArrayList<>();

    // 查设备
    public Optional<DeviceConfig> findDevice (String deviceId) {
        return devices.stream().filter(d -> d.getId().equals(deviceId))
                .findFirst();
    }
}
