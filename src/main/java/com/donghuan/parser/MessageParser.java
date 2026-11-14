package com.donghuan.parser;

import com.donghuan.common.DeviceConfig;
import com.donghuan.common.DeviceProperties;
import com.donghuan.model.PointValue;
import com.donghuan.model.Protocol;
import com.donghuan.model.RawMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageParser {

    private final DeviceProperties deviceProperties;
    private final Map<Protocol, ProtocolMessageParser> parsers;

    public MessageParser(DeviceProperties deviceProperties, List<ProtocolMessageParser> parsers) {
        this.deviceProperties = deviceProperties;
        this.parsers = parsers.stream()
                .collect(Collectors.toMap(ProtocolMessageParser::protocol, Function.identity()));
    }

    public List<PointValue> parse(RawMessage raw) {
        DeviceConfig device = deviceProperties.findDevice(raw.getDeviceCode()).orElse(null);
        if (device == null) {
            return Collections.emptyList();
        }

        ProtocolMessageParser parser = parsers.get(raw.getProtocol());
        if (parser == null) {
            log.warn("未找到协议解析器：deviceCode={}，protocol={}",
                    raw.getDeviceCode(), raw.getProtocol());
            return Collections.emptyList();
        }
        return parser.parse(raw, device);
    }
}