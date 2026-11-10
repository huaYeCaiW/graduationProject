package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.PointValue;
import com.donghuan.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {
    private final SnapshotService snapshotService;

    @GetMapping
    public Map<String, PointValue> snapshots(@RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return snapshotService.findAllSnapshots(user);
    }
}
