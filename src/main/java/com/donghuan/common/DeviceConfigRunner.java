package com.donghuan.common;

import com.donghuan.model.PointValue;
import com.donghuan.model.RawMessage;
import com.donghuan.parser.MessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceConfigRunner implements ApplicationRunner {

    private final DeviceProperties deviceProperties;
    private final MessageParser messageParser;

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
        RawMessage raw = new RawMessage();
        raw.setDeviceId("th-01");
        raw.setProtocol("mqtt");
        raw.setPayload("{\"temperature\":25.5,\"humidity\":60}");
        raw.setReceivedAt(Instant.now());

        List<PointValue> result = messageParser.parse(raw);
        System.out.println("解析结果数量：" + result.size());
        result.forEach(pv ->  System.out.println(pv));

    }
}
