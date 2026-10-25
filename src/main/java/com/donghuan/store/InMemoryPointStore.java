package com.donghuan.store;

import com.donghuan.common.DeviceProperties;
import com.donghuan.model.PointValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "donghuan.store.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryPointStore implements PointStore {

    private final DeviceProperties deviceProperties = new DeviceProperties();
    private final Map<String, List<PointValue>> history = new ConcurrentHashMap<>();

    //存储
    @Override
    public void save(PointValue value) {
        //拼key"th-01:temperature"
        String key = value.getDeviceId() + ":" +value.getPointId();
        //如果key不存在，new一个新的；存在返回这个list
        history.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                .add(value);

        log.debug("采样库存：{}", value);
    }

    //查最新值
    @Override
    public Optional<PointValue> findLatest(String deviceId, String pointId) {
        //拼key
        String key = deviceId + ":" + pointId;
        List<PointValue> list = history.get(key);
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.getLast());
    }

    //查某时间范围内值
    @Override
    public List<PointValue> findByRange(String deviceId, String pointId, Instant startTime, Instant endTime) {

        //拼key"th-01:temperature"
        String key = deviceId + ":" + pointId;
        //取数组
        List<PointValue> list = history.get(key);
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(pv -> !pv.getTimestamp().isBefore(startTime)
                                     && !pv.getTimestamp().isAfter(endTime))
                .toList();
    }

    @Override
    public boolean updateThreshold(String deviceId, String pointId, Double min, Double max) {

        return deviceProperties.findDevice(deviceId)
                .flatMap(d -> d.findPoint(pointId))
                .map(point -> {
                    if (max != null) {
                        point.setMax(max);
                    }
                    if (min != null) {
                        point.setMin(min);
                    }
                    return true;
                }).orElse(false);
    }
}
