package com.donghuan.common;

import com.donghuan.model.PointValue;
import com.donghuan.model.RawMessage;
import com.donghuan.parser.MessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceConfigRunner implements ApplicationRunner {

    private final DeviceProperties deviceProperties;
    private final MessageParser messageParser;

    @Override
    public void run(ApplicationArguments args) {

    }
}
