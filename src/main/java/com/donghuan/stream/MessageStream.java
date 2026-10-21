package com.donghuan.stream;

import com.donghuan.model.PointValue;
import com.donghuan.model.RawMessage;
import com.donghuan.parser.MessageParser;
import com.donghuan.pipeline.ThresholdChecker;
import com.donghuan.store.InMemoryPointStore;
import com.donghuan.store.LatestValueHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStream {

    private final MessageParser parser;
    private final ThresholdChecker thresholdChecker;
    private final InMemoryPointStore pointStore;
    private final LatestValueHolder latestHolder;
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
        Flux<PointValue> valuesFlux = rawSink.asFlux()                           // 订阅端形态Sinks.many() -> Flux
                .map(parser::parse)      // RawMessage -> List<PointValue>
                .flatMapIterable(list -> list);  // List -> 拆成一个个 PointValue

        // 订阅
        //更新最新值
        valuesFlux.subscribe(
                latestHolder::update,
                err -> log.error("最新值更新失败", err)
        );
        //越限警告
        valuesFlux.filter(thresholdChecker::isOutOfRange)
                .subscribe(
                        thresholdChecker::raiseAlarm,
                        err -> log.error("警告失败", err)
                );
        //采样存储
        valuesFlux.subscribe(
                        pointStore::save,
                        err -> log.error("采样存储失败", err)
                );
    }

    //对外入口，报文推入口
    public void emit(RawMessage raw) {
        rawSink.tryEmitNext(raw);
    }
}
