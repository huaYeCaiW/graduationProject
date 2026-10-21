package com.donghuan.adapter;

import com.donghuan.common.DeviceConfig;
import com.donghuan.model.RawMessage;

import java.util.function.Consumer;

public interface ProtocolAdapter {

    public String protocol();
    //建立连接
    void connect(DeviceConfig device);
    //订阅
    void subscribe(DeviceConfig device, Consumer<RawMessage> sink);
    //断开连接
    void disconnect();
}
