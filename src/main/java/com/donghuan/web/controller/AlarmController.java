package com.donghuan.web.controller;

import com.donghuan.model.AlarmValue;
import com.donghuan.store.AlarmStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {
    private final AlarmStore alarmStore;
    private static final long TEST_USER_ID = 1L;


    @GetMapping("/active")
    public List<AlarmValue> listActive() {
        return alarmStore.findActive();
    }

    @GetMapping
    public List<AlarmValue> listAll() {
        return alarmStore.findAll();
    }

    // TODO五阶段改为Session中登陆的userId
    @PostMapping("/{id}/confirm")
    public int confirm(@PathVariable long id) {
        return alarmStore.confirm(id, TEST_USER_ID, Instant.now());
    }
}
