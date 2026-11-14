package com.donghuan.stream;

import com.donghuan.common.DeviceProperties;
import com.donghuan.common.PointKeys;
import com.donghuan.model.PointValue;
import com.donghuan.model.RawMessage;
import com.donghuan.parser.MessageParser;
import com.donghuan.pipeline.DataCleaner;
import com.donghuan.service.AlarmService;
import com.donghuan.store.PointSnapshot;
import com.donghuan.store.PointStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStream {

    private static final int DEFAULT_SAMPLE_SECONDS = 3;

    private final PointKeys keys;
    private final MessageParser parser;
    private final PointStore pointStore;
    private final PointSnapshot snapshots;
    private final SnapshotBroadcaster snapshotBroadcaster;
    private final AlarmService alarmService;
    private final DataCleaner cleaner;
    private final DeviceProperties deviceProperties;
    private final Map<String, Instant> saved = new ConcurrentHashMap<>();

    // 流字段
    private Sinks.Many<RawMessage> rawSink;

    @PostConstruct
    public void init(){
        // 热流入口.multicast()一份数据所有订阅共享
        // .onBackpressureBuffer()下游处理慢时先进入缓冲区
        rawSink = Sinks.many().multicast().onBackpressureBuffer();

        // 订阅端形态Sinks.many() -> Flux
        // RawMessage -> List<PointValue>
        Flux<PointValue> valuesFlux = rawSink.asFlux()// 订阅端形态Sinks.many() -> Flux
                .map(parser::parse)                   // RawMessage -> List<PointValue>
                .flatMapIterable(list -> list)// List -> 拆成一个个 PointValue
                .doOnNext(cleaner::convertUnit)
                .map(cleaner::verify)// 校验
                .filter(cleaner::accept)
                .share();

        // 订阅
        // 内存快照
        valuesFlux.subscribe(pv -> {
            try {
                snapshots.snapshot(pv);
                snapshotBroadcaster.publish(pv);
            } catch (Exception e) {
                log.error("快照失败：{}", pv, e);
            }
        }, err -> log.error("快照流异常终止", err));

        //越限警告
        valuesFlux.subscribe(pv -> {
            try {
                alarmService.evaluate(pv);
            } catch (Exception e) {
                log.error("警告处理失败:{}", pv, e);
            }
        },err -> log.error("告警流异常终止", err));

        //采样存储：按测点 freq_seconds 节流，未配置默认 3 秒
        valuesFlux.subscribe(pv ->{
            String key = keys.key(pv.getDeviceCode(), pv.getPointCode());
            Instant last = saved.get(key);
            Duration interval = Duration.ofSeconds(sampleSeconds(pv));
            // 内存空，直接落库，并存内存
            if (last == null) {
                try {
                    pointStore.save(pv);
                    saved.put(key, Instant.now());
                } catch (Exception e) {
                    log.error("存储失败:{}", pv, e);
                }
                // 内存非空，间隔达到测点采样周期才落库
            } else if (Duration.between(last, Instant.now()).compareTo(interval) >= 0) {
                try {
                    pointStore.save(pv);
                    saved.put(key, Instant.now());
                } catch (Exception e) {
                    log.error("存储失败:{}", pv, e);
                }
            }
        },err -> log.error("存储流异常终止", err));
    }

    // 取测点配置的采样周期，未配置默认 3 秒
    private int sampleSeconds(PointValue pv) {
        Integer freq = deviceProperties.findDevice(pv.getDeviceCode())
                .flatMap(device -> device.findPoint(pv.getPointCode()))
                .map(point -> point.getFreq())
                .orElse(null);
        return freq == null || freq <= 0 ? DEFAULT_SAMPLE_SECONDS : freq;
    }

    //对外入口，报文推入口
    public void emit(RawMessage raw) {
        rawSink.tryEmitNext(raw);
    }
}
