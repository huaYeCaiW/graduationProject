package com.donghuan.store;

import com.donghuan.model.PointValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LatestValueHolder {

    // 并发安全——Reactor流回调可能多线程，普通HashMap会并发出问题
    private final Map<String, PointValue> latest = new ConcurrentHashMap<>();

    // 拼接key避免不同设备不同测点突
    public void update(PointValue pv) {
        latest.put(pv.getDeviceId() + ":" + pv.getPointId(), pv);
    }

    // RESt API 查询实时值时调
    public Optional<PointValue> get(String deviceId, String pointId) {
        return Optional.ofNullable(latest.get(deviceId + ":" + pointId));
    }
}
