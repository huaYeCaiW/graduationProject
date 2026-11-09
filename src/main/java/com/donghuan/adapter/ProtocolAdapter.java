package com.donghuan.adapter;

import com.donghuan.common.DeviceConfig;
import com.donghuan.model.Protocol;

public interface ProtocolAdapter {

    public Protocol protocol();
    //建立连接
    void connect(DeviceConfig device);
    //断开连接
    void disconnect(String clientId);
}
