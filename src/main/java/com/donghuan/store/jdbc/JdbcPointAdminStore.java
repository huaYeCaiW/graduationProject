package com.donghuan.store.jdbc;

import com.donghuan.model.PointCommand;
import com.donghuan.store.PointAdminStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcPointAdminStore implements PointAdminStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insert(long deviceId, PointCommand point) {
        String sql = "insert into point " +
                "(device_id, point_code, name, address, time_address, data_type, " +
                "unit, freq_seconds, min_value, max_value, range_min, range_max, enabled) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
        jdbcTemplate.update(sql,
                deviceId, point.pointCode(), point.name(), point.address(),
                point.timeAddress(), point.dataType(), point.unit(),
                point.freqSeconds(), point.minValue(), point.maxValue(),
                point.rangeMin(), point.rangeMax());
    }

    @Override
    public void update(long deviceId, String pointCode, PointCommand point) {
        String sql = "update point set name = ?, address = ?, time_address = ?, data_type = ?, " +
                "unit = ?, freq_seconds = ?, min_value = ?, max_value = ?, " +
                "range_min = ?, range_max = ? " +
                "where device_id = ? and point_code = ? and enabled = 1";
        jdbcTemplate.update(sql,
                point.name(), point.address(), point.timeAddress(), point.dataType(),
                point.unit(), point.freqSeconds(), point.minValue(), point.maxValue(),
                point.rangeMin(), point.rangeMax(), deviceId, pointCode);
    }

    @Override
    public void disable(long deviceId, String pointCode) {
        jdbcTemplate.update(
                "update point set enabled = 0 " +
                        "where device_id = ? and point_code = ? and enabled = 1",
                deviceId, pointCode);
    }
}
