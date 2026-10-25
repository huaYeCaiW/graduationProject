package com.donghuan.common;

import com.donghuan.parser.MessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order
public class DeviceConfigRunner implements ApplicationRunner {

    private final DeviceProperties deviceProperties;
    private final MessageParser messageParser;

    @Override
    public void run(ApplicationArguments args) {

    }
}
