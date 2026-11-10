package com.donghuan.store.jdbc;

import com.donghuan.common.DeviceProperties;
import com.donghuan.model.PointValue;
import com.donghuan.model.Quality;
import com.donghuan.model.TenantScope;
import com.donghuan.store.PointRefCache;
import com.donghuan.store.PointStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcPointStore implements PointStore {

    private final JdbcTemplate jdbcTemplate;
    private final DeviceProperties deviceProperties;
    private final PointRefCache refCache;

    @Override
    public void save(PointValue value) {

        OptionalLong ref = refCache.find(value.getDeviceCode(), value.getPointCode());

        if (ref.isEmpty()){
            log.warn("点位未映射，跳过存值：{} : {}", value.getDeviceCode(), value.getPointCode());
            return;
        }

        String sql = "insert into point_value " +
                "(point_id, value, sample_at, sys_ts, quality) " +
                "values(?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                ref.getAsLong(),
                value.getValue(),
                Timestamp.from(value.getSampledAt()),
                Timestamp.from(Instant.now()),// 把参数转换成Timestamp.from()
                value.getQuality()
        );
        log.debug("采样库存：{}", value);
    }

    @Override
    public Optional<PointValue> findLatest(TenantScope scope, String deviceCode, String pointCode) {
        if (!canAccess(scope, deviceCode)) {
            return Optional.empty();
        }

        // 获取point_value表中point_id类型为bigint
        OptionalLong ref = refCache.find(deviceCode, pointCode);
        if (ref.isEmpty()) return Optional.empty();

        String sql = "select value, sample_at, quality " +
                "from point_value " +
                "where point_id = ? and quality = ? " +
                "order by sample_at desc, id desc limit 1";

        List<PointValue> list = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    PointValue pv = new PointValue();
                            pv.setDeviceCode(deviceCode);
                            pv.setPointCode(pointCode);
                            pv.setValue(rs.getDouble("value"));
                            pv.setSampledAt(rs.getTimestamp("sample_at").toInstant());
                            pv.setQuality(rs.getString("quality"));
                            return pv;
                }, ref.getAsLong(), Quality.OK.name());
        return list.stream().findFirst();
    }

    @Override
    public List<PointValue> findByRange(TenantScope scope, String deviceCode, String pointCode, Instant startTime, Instant endTime) {
        if (!canAccess(scope, deviceCode)) {
            return List.of();
        }

        OptionalLong ref = refCache.find(deviceCode, pointCode);
        if (ref.isEmpty()) return List.of();

        String sql = "select value, sample_at, quality from point_value " +
                "where point_id = ? and quality = ? " +
                "and sample_at between ? and ? order by sample_at";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    PointValue pv = new PointValue();
                    pv.setDeviceCode(deviceCode);
                    pv.setPointCode(pointCode);
                    pv.setValue(rs.getDouble("value"));
                    pv.setQuality(rs.getString("quality"));
                    pv.setSampledAt(rs.getTimestamp("sample_at").toInstant());
                    return pv;
                }, ref.getAsLong(),Quality.OK.name(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    @Override
    public boolean updateThreshold(TenantScope scope, String deviceCode, String pointCode, Double min, Double max) {
        if (!canAccess(scope, deviceCode)) {
            return false;
        }

        OptionalLong ref = refCache.find(deviceCode, pointCode);

        if (ref.isEmpty()) return false;
        else {
            int row = jdbcTemplate.update("update point " +
                            "set min_value = coalesce(?, min_value), " +    //coalesce(?, a)?为null传a,防止传null破坏数据
                            "max_value = coalesce(?, max_value) " +
                            "where id = ?",
                    min, max, ref.getAsLong());

            if (row == 0) return false;
            // 同步内存
            deviceProperties.findDevice(deviceCode)
                    .flatMap(d -> d.findPoint(pointCode))
                    .ifPresent(point -> {
                        if (max != null) point.setMax(max);
                        if (min != null) point.setMin(min);
                    });

            return true;
        }
    }

    // 设备归属校验
    private boolean canAccess(TenantScope scope, String deviceCode) {
        if (scope.seeAll()) {
            return true;
        }
        return deviceProperties.findDevice(deviceCode)
                .map(d -> scope.canSee(d.getTenantId()))
                .orElse(false);
    }
}
