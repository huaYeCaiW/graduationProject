package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.PointValue;
import com.donghuan.model.TenantScope;
import com.donghuan.service.SnapshotService;
import com.donghuan.stream.SnapshotBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {
    private final SnapshotService snapshotService;
    private final SnapshotBroadcaster snapshotBroadcaster;

    @GetMapping
    public Map<String, PointValue> snapshots(@RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return snapshotService.findAllSnapshots(user);
    }

    // SSE 实时推送：连接时全量，之后每 500ms 推一次增量
    @GetMapping("/stream")
    public SseEmitter stream(@RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return snapshotBroadcaster.subscribe(
                TenantScope.of(user), snapshotService.findAllSnapshots(user));
    }
}
