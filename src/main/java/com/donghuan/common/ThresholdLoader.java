package com.donghuan.common;

import com.donghuan.store.PointRefCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "donghuan.store.type", havingValue = "jdbc")
@Order(2)
public class ThresholdLoader implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DeviceProperties deviceProperties;
    private final PointRefCache refCache;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int[] loaded = {0};
        jdbcTemplate.query(
                "select " +
                        "d.device_code, " +
                        "p.point_code, " +
                        "p.min_value, " +
                        "p.max_value " +
                        "from point p join device d on p.device_id = d.id " +
                        "where p.enabled = 1 and d.enabled = 1",
                rs -> {
                    String pointId = rs.getString("point_code");
                    String deviceId = rs.getString("device_code");
                    Double min = rs.getObject("min_value", Double.class);
                    Double max = rs.getObject("max_value", Double.class);

                    deviceProperties.findDevice(deviceId)
                            .flatMap(d -> d.findPoint(pointId))
                            .ifPresent(p -> {
                                if (min != null) {
                                    p.setMin(min);
                                }
                                if (max != null) {
                                    p.setMax(max);
                                }
                                loaded[0]++;
                            });
                });
        log.info("阈值加载完成：{}条", loaded[0]);
    }
}
