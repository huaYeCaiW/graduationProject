package com.donghuan.pipeline;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.common.PointKeys;
import com.donghuan.model.PointValue;
import com.donghuan.model.Quality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleaner {

    private final Map<String, Instant> lastAcceptedAt = new ConcurrentHashMap<>();
    private final PointKeys keys;
    private final DeviceProperties deviceProperties;

    // 校验：按测点物理量程判定，量程为空表示不校验
    public PointValue verify(PointValue pv) {
        DeviceConfig.Point point = deviceProperties.findDevice(pv.getDeviceCode())
                .flatMap(device -> device.findPoint(pv.getPointCode()))
                .orElse(null);

        boolean invalid = point != null
                && ((point.getRangeMin() != null && pv.getValue() < point.getRangeMin())
                || (point.getRangeMax() != null && pv.getValue() > point.getRangeMax()));

        if (invalid) {
            log.debug("非法值deviceCode：{}，pointCode：{}，value：{}",
                    pv.getDeviceCode(), pv.getPointCode(), pv.getValue());
            pv.setQuality(Quality.BAD.name());
        } else {
            pv.setQuality(Quality.OK.name());
        }
        return pv;
    }

    // 去重
    public boolean accept (PointValue pv) {
        String key = keys.key(pv.getDeviceCode(), pv.getPointCode());
        Instant ts = pv.getSampledAt();
        if (ts == null) {
            log.debug("时间为空，不做去重deviceCode：{}，pointCode：{}", pv.getDeviceCode(), pv.getPointCode());
            return true;
        }
        // 可在compute中调用
        AtomicBoolean accepted = new AtomicBoolean(true);

        lastAcceptedAt.compute(key, (k, last) -> {
            // last == ts重复
            if (last != null && last.equals(ts)) {
                accepted.set(false);
                return last;
            }
            return ts;
        });
        if (!accepted.get()) log.debug("重复deviceCode：{}， pointCode：{}", pv.getDeviceCode(), pv.getPointCode());
        return accepted.get();
    }

    // 单位转换
    public void convertUnit (PointValue pv) {
        if ("℉".equals(pv.getUnit())) {
            pv.setValue((pv.getValue() - 32) / 1.8);
            pv.setUnit("℃");
            log.debug("deviceCode：{}，pointCode：{}单位“℉”已转为“℃”", pv.getDeviceCode(), pv.getPointCode());
        }
    }
}
