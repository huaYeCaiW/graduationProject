package com.donghuan.store;

import com.donghuan.model.DeviceCommand;
import com.donghuan.model.TenantScope;

import java.util.List;

public interface DeviceStore {

    // 新增设备
    void insert(DeviceCommand device);

    // 修改设备：deviceCode 为路径上的原编码，不作为可改字段
    void update(DeviceCommand device);

    // 逻辑删除设备及其测点
    void disable(String deviceCode);

    // 按编码取逻辑删除的设备，恢复路径用来校验租户
    DeviceCommand findDisabled(String deviceCode);

    // 已删除设备列表，恢复入口用
    List<DeviceCommand> findDisabledDevices(TenantScope scope);

    // 恢复逻辑删除的设备及其测点
    void restore(DeviceCommand device);

    // 按编码取设备主键，测点写库要用
    Long findId(String deviceCode);
}
