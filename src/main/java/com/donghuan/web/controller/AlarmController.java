package com.donghuan.web.controller;

import com.donghuan.model.AlarmValue;
import com.donghuan.store.AlarmStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {
    private final AlarmStore alarmStore;

    @GetMapping("/active")
    public List<AlarmValue> listActive() {
        return alarmStore.findActive();
    }

    @GetMapping
    public List<AlarmValue> listAll() {
        return alarmStore.findAll();
    }
}
