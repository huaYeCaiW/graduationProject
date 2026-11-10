package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.AlarmValue;
import com.donghuan.model.LoginUser;
import com.donghuan.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {
    private final AlarmService alarmService;

    @GetMapping("/active")
    public List<AlarmValue> listActive(@RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return alarmService.listActive(user);
    }

    @GetMapping
    public List<AlarmValue> listAll(@RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return alarmService.listAll(user);
    }

    @PostMapping("/{id}/confirm")
    public void confirm(@PathVariable long id,
                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        alarmService.confirm(user, id);
    }
}
