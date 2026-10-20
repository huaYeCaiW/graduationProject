package com.donghuan.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceConfigRunner implements ApplicationRunner {

    private final DeviceProperties deviceProperties;

    @Override
    public void run(ApplicationArguments args) {
        log.info("共加载{}个设备配置", deviceProperties.getDevices().size());
        deviceProperties.getDevices().forEach(device -> {
            log.info("设备：id={}, name={}, protoccol={}, topic={}, 测点={}，" +
                    device.getId(),
                    device.getName(),
                    device.getProtocol(),
                    device.getConnection() != null ? device.getConnection().getTopic():"-",
                    device.getPoints().size());
            device.getPoints().forEach(point ->
                    log.info("测点：{} [{}] unit={} jsonPath={} freq={}",
                            point.getPointId(),point.getMetric(),point.getUnit(),
                            point.getJsonPath(),point.getFreq()));
        });
    }
}
