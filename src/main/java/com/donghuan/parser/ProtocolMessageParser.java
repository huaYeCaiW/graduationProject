package com.donghuan.parser;

import com.donghuan.common.DeviceConfig;
import com.donghuan.model.PointValue;
import com.donghuan.model.Protocol;
import com.donghuan.model.RawMessage;

import java.util.List;

public interface ProtocolMessageParser {

    Protocol protocol();

    List<PointValue> parse(RawMessage raw, DeviceConfig device);
}