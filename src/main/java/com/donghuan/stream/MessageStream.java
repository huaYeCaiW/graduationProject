package com.donghuan.stream;

import com.donghuan.model.PointValue;
import com.donghuan.model.RawMessage;
import com.donghuan.parser.MessageParser;
import com.donghuan.pipeline.DataCleaner;
import com.donghuan.service.AlarmService;
import com.donghuan.store.PointStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStream {

    private final MessageParser parser;
    private final PointStore pointStore;
    private final AlarmService alarmService;
    private final DataCleaner cleaner;
    // 采样周期
    private final Duration sampleInterval = Duration.ofSeconds(3);

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
                .flatMap(pv -> Mono.justOrEmpty(cleaner.verify(pv)))// 校验
                .filter(cleaner::accept)
                .share();

        // 订阅
        //越限警告
        valuesFlux.subscribe(pv -> {
            try {
                alarmService.evaluate(pv);
            } catch (Exception e) {
                log.error("警告处理失败:{}", pv, e);
            }
        },err -> log.error("告警流异常终止", err));

        //采样存储
        valuesFlux.subscribe(pv ->{
            try {
                pointStore.save(pv);
            } catch (Exception e) {
                log.error("存储失败:{}", pv, e);
            }
        },err -> log.error("存储流异常终止", err));
    }

    //对外入口，报文推入口
    public void emit(RawMessage raw) {
        rawSink.tryEmitNext(raw);
    }
}
