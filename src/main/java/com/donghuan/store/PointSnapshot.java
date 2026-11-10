package com.donghuan.store;

import com.donghuan.common.DeviceProperties;
import com.donghuan.common.PointKeys;
import com.donghuan.model.PointValue;
import com.donghuan.model.Quality;
import com.donghuan.model.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointSnapshot implements LatestValueStore {

    private final Map<String, PointValue> latestSnapshot = new ConcurrentHashMap<>();
    private final PointKeys keys;
    private final DeviceProperties deviceProperties;

    @Override
    public void snapshot(PointValue pv) {
        String key = keys.key(pv.getDeviceCode(), pv.getPointCode());

        latestSnapshot.compute(key, (k, p) -> {
            // 确认 pv 质量 BAD 返回 p
            if (Quality.OK.name().equals(pv.getQuality())) {
                // p 为空 插入 pv
                if (p == null) return pv;
                // p 不为空，判定时间先后，<0 p时间在前
                else if (p.getSampledAt().compareTo(pv.getSampledAt()) < 0)
                    return pv;
            }
            return p;
        });
        log.debug("快照{}", pv);
    }

    // 返回快照
    @Override
    public Map<String, PointValue> findAllSnapshots(TenantScope scope) {
        if (scope.seeAll()) return Map.copyOf(latestSnapshot);

        Map<String, PointValue> result = new HashMap<>();
        latestSnapshot.forEach((key, value) -> {
            if (deviceProperties.findDevice(value.getDeviceCode())
                    .map(d -> scope.canSee(d.getTenantId()))
                    .orElse(false)) {
                result.put(key, value);
            }
        });
        return result;
    }
}
