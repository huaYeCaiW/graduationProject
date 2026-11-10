package com.donghuan.service;

import com.donghuan.model.*;
import com.donghuan.pipeline.ThresholdChecker;
import com.donghuan.store.AlarmStore;
import com.donghuan.store.PointRefCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
public class AlarmService implements ApplicationRunner {

    // 内存中的alarm,key是pointId
    private final Map<Long, AlarmValue> activeByPoint = new ConcurrentHashMap<>();

    private final AlarmStore alarmStore;
    private final ThresholdChecker thresholdChecker;
    private final PointRefCache refCache;

    @Override
    public void run(ApplicationArguments args) {

        alarmStore.findAllActive().forEach(a -> activeByPoint.put(a.getPointId(), a));
        log.info("警告回填{}条", activeByPoint.size());
    }

    public void evaluate(PointValue pv) {
        // 校验结果BAD直接跳过
        if (pv.getQuality().equals(Quality.BAD.name())) return;
        // 取映射id,alarm.point_id,是point的自增id对应一个point
        OptionalLong optionalPointId = refCache.find(pv.getDeviceCode(), pv.getPointCode());
        // 存阈值判断后的result
        Optional<ThresholdChecker.ThresholdResult> optionalResult = thresholdChecker.check(pv);
        if (optionalResult.isEmpty()) return;
        if (optionalPointId.isEmpty()) return;
        // 取optional中的result
        ThresholdChecker.ThresholdResult result = optionalResult.get();
        // 内存中的alarmValue
        AlarmValue activeAlarm = activeByPoint.get(optionalPointId.getAsLong());

        Instant sampleAt = pv.getSampledAt() != null ? pv.getSampledAt() : Instant.now();

        if (result.breached()) {
            if (activeAlarm == null) {
                // 组装value
                AlarmValue av = new AlarmValue();
                av.setPointId(optionalPointId.getAsLong());
                av.setLimitType(result.limitType());
                av.setThreshold(result.threshold());
                av.setValue(pv.getValue());
                av.setSampleAt(sampleAt);

                long id = alarmStore.insert(av);
                // 回填自增id
                av.setId(id);
                // 新警告put内存
                activeByPoint.put(optionalPointId.getAsLong(), av);
                log.debug("新警告{}", av);

            } else {
                // 更新alarm表
                alarmStore.touch(activeAlarm.getId(), pv.getValue());
                // 回填内存
                activeAlarm.setValue(pv.getValue());
                log.debug("更新值{}", pv.getValue());

            }
        } else if (activeAlarm != null){
            // 标记恢复
            alarmStore.markRecovered(activeAlarm.getId(), sampleAt);
            activeByPoint.remove(activeAlarm.getPointId());
            log.debug("恢复{}", activeAlarm);
        }
    }

    public List<AlarmValue> listActive(LoginUser user) {
        TenantScope scope = TenantScope.of(user);
        return alarmStore.findActive(scope);
    }

    public List<AlarmValue> listAll(LoginUser user) {
        TenantScope scope = TenantScope.of(user);
        return alarmStore.findAll(scope);
    }

    public void confirm(LoginUser user, long alarmId) {
        TenantScope scope = TenantScope.of(user);
        boolean ok = alarmStore.confirm(scope, alarmId, user.getId(), Instant.now());
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
