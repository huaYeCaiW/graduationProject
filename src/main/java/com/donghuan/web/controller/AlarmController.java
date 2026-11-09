package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.AlarmValue;
import com.donghuan.model.LoginUser;
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

    @GetMapping("/active")
    public List<AlarmValue> listActive() {
        return alarmStore.findActive();
    }

    @GetMapping
    public List<AlarmValue> listAll() {
        return alarmStore.findAll();
    }

    @PostMapping("/{id}/confirm")
    public int confirm(@PathVariable long id,
                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return alarmStore.confirm(id, user.getId(), Instant.now());
    }
}
