package com.donghuan.acquire;

import com.donghuan.adapter.ProtocolAdapter;
import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.model.Protocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcquisitionService implements CommandLineRunner {

    private final DeviceProperties deviceProperties;
    private final List<ProtocolAdapter> adapters;

    private Map<Protocol, ProtocolAdapter> adapterMap;

    @Override
    public void run(String... args) throws Exception {
        //适配器注册表
        adapterMap = adapters.stream()
                .collect(Collectors.toMap(ProtocolAdapter::protocol, Function.identity()));
        //遍历设备
        for (DeviceConfig device : deviceProperties.getDevices()) {
            ProtocolAdapter adapter = adapterMap.get(device.getProtocol());
            if (adapter == null) {
                log.warn("未找到协议适配器:deviceId:{}: {}", device.getId(), device.getProtocol());
                continue;
            }

            adapter.connect(device);
            log.info("设备接入完成：id={}，protocol={}", device.getId(), device.getProtocol());
        }
    }
}
