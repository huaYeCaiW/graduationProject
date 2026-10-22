package com.donghuan.store.jdbc;

import com.donghuan.common.DeviceProperties;
import com.donghuan.model.PointValue;
import com.donghuan.store.PointStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "donghuan.store.type", havingValue = "jdbc")
public class JdbcPointStore implements PointStore {

    private final JdbcTemplate jdbcTemplate;
    private final DeviceProperties deviceProperties;

    @Override
    public void save(PointValue value) {
        String sql = "insert into point_value" +
                                        "(device_id, point_id, value, sys_ts, sample_at)" +
                                        "values(?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                value.getDeviceId(),
                value.getPointId(),
                value.getValue(),
                Timestamp.from(value.getTimestamp()),   // MySQL对setObject(Instant)支持不稳定
                Timestamp.from(value.getSampledAt())    // 把参数转换成Timestamp.from()
        );
        log.info("采样库存：{}", value);
    }

    @Override
    public Optional<PointValue> findLatest(String deviceId, String pointId) {
        String sql = "select * from point_value " +
                    "where device_id = ? and point_id = ? " +
                    "order by sample_at desc limit 1";

        try {
            //`this::mapRow` 是 方法引用，
            // 它的作用是把这个方法本身作为值传给`queryForObject`。
            // `RowMapper`
            // 是一个只有`mapRow(ResultSet rs, int rowNum)` 这一个抽象方法的函数式接口，
            // Spring 拿到它之后，会对每一行调用一次。
            return Optional.of(
                    jdbcTemplate.queryForObject(
                            sql, this::mapRow, deviceId, pointId)
            );
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<PointValue> findByRange(String deviceId, String pointId, Instant startTime, Instant endTime) {
        String sql = "select * from point_value " +
                "where device_id = ? and point_id = ? " +
                "and sample_at between ? and ? order by sample_at asc";

        return jdbcTemplate.query(
                sql,
                this::mapRow,
                deviceId,
                pointId,
                Timestamp.from(startTime),
                Timestamp.from(endTime));
    }


    //将查询数据拼成PointValue因为java无法直接返回多个值
    private PointValue mapRow(ResultSet rs, int rowNum) throws SQLException {
        PointValue pv = new PointValue();

        // rs指到这一行数据由rs.get??()读取
        pv.setDeviceId(rs.getString("device_id"));
        pv.setPointId(rs.getString("point_id"));
        pv.setTimestamp(rs.getTimestamp("sys_ts").toInstant());
        pv.setSampledAt(rs.getTimestamp("sample_at").toInstant());
        pv.setValue(rs.getDouble("value"));

        return pv;
    }

    @Override
    public boolean updateThreshold(String deviceId, String pointId, Double min, Double max) {
        int row = jdbcTemplate.update("update point set " +
                    "min_value = coalesce(?, min_value), " +
                    "max_value = coalesce(?, max_value) " +
                    "where device_id = ? and point_id = ?",
                    min, max, deviceId, pointId);

        if (row == 0) {
            return false;
        } else {
            return deviceProperties.findDevice(deviceId)
                    .flatMap(d -> d.findPoint(pointId))
                    .map(point -> {
                        if (max != null) {
                            point.setMax(max);
                        }
                        if (min != null) {
                            point.setMin(min);
                        }
                        return true;
                    }).orElse(false);
        }


    }
}
