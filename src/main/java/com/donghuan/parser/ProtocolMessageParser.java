package com.donghuan.parser;

import com.donghuan.common.DeviceConfig;
import com.donghuan.model.domain.PointValue;
import com.donghuan.model.enums.Protocol;
import com.donghuan.model.domain.RawMessage;

import java.util.List;

public interface ProtocolMessageParser {

    Protocol protocol();

    List<PointValue> parse(RawMessage raw, DeviceConfig device);
}