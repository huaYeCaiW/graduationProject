package com.donghuan.store;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointRefCache {

    private final JdbcTemplate jdbcTemplate;
    private volatile Map<String, Long> refs = Map.of();

    @PostConstruct
    public void load() {

        Map<String, Long> next = new HashMap<>();
        // 查point拼key，传入内存
        jdbcTemplate.query("select p.id, d.device_code, p.point_code from " +
                "point p join device d " +
                "on p.device_id = d.id " +
                "where p.enabled = 1 and d.enabled = 1",
                // 按行取数据rs指向当前行，JdbcTemplate内部会执行循环rs.next()，callback.processRow(rs)
                rs -> {
                    next.put(key(rs.getString("device_code"), rs.getString("point_code")),
                            rs.getLong("id"));
                });
        refs = Map.copyOf(next);
        if (next.isEmpty())
        {
            log.warn("点位映射无");
        } else {
            log.info("点位映射加载{}条", next.size());
        }
    }

    public void refresh() {
        load();
    }

    public OptionalLong find(String deviceCode, String pointCode) {

        Long id = refs.get(key(deviceCode, pointCode));

        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    // 拼接key“deviceCode:pointCode”
    private static String key (String deviceCode, String pointCode) {
        return deviceCode + ":" + pointCode;
    }
}
