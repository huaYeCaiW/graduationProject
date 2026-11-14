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
                log.warn("未找到协议适配器:deviceCode:{}: {}", device.getDeviceCode(), device.getProtocol());
                continue;
            }

            adapter.connect(device);
            log.info("设备接入完成：deviceCode={}，protocol={}", device.getDeviceCode(), device.getProtocol());
        }
    }

    // 按设备协议找到适配器并连接，供新增设备/新增测点后热生效
    public void connect(DeviceConfig device) {
        if (adapterMap == null) {
            return;
        }
        ProtocolAdapter adapter = adapterMap.get(device.getProtocol());
        if (adapter == null) {
            log.warn("未找到协议适配器：deviceCode={}，protocol={}",
                    device.getDeviceCode(), device.getProtocol());
            return;
        }
        try {
            adapter.connect(device);
        } catch (Exception e) {
            log.warn("设备接入失败：deviceCode={}", device.getDeviceCode(), e);
        }
    }

    // 按设备协议找到适配器并断开，供删除接口调用
    public void disconnect(DeviceConfig device) {
        if (adapterMap == null) {
            return;
        }
        ProtocolAdapter adapter = adapterMap.get(device.getProtocol());
        if (adapter != null) {
            adapter.disconnect(device.getDeviceCode());
        }
    }
}
