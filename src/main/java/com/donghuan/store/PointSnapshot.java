package com.donghuan.store;

import com.donghuan.common.PointKeys;
import com.donghuan.model.PointValue;
import com.donghuan.model.Quality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointSnapshot implements LatestValueStore {

    public final Map<String, PointValue> latestSnapshot = new ConcurrentHashMap<>();
    private final PointKeys keys;

    @Override
    public void snapshot(PointValue pv) {
        String key = keys.key(pv.getDeviceId(), pv.getPointId());

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

    @Override
    // 直接返回内存副本
    public Map<String, PointValue> findAllSnapshots() {
        return Map.copyOf(latestSnapshot);
    }
}
