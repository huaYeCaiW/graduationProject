package com.donghuan.store;

import com.donghuan.model.PointCommand;

public interface PointAdminStore {

    // 新增测点
    void insert(long deviceId, PointCommand point);

    // 修改测点：pointCode 为路径上的原编码，不作为可改字段
    void update(long deviceId, String pointCode, PointCommand point);

    // 逻辑删除测点
    void disable(long deviceId, String pointCode);
}
