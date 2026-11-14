package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.PointCommand;
import com.donghuan.model.PointValue;
import com.donghuan.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping("/{deviceCode}/{pointCode}/latest")
    public PointValue latest(@PathVariable String deviceCode, @PathVariable String pointCode,
                                    @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return pointService.findLatest(user, deviceCode, pointCode);
    }

    @GetMapping("/{deviceCode}/{pointCode}/range")
    public List<PointValue> range(@PathVariable String deviceCode,
                                  @PathVariable String pointCode,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
                                  @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user
                                  ) {
        return pointService.findByRange(user, deviceCode, pointCode, start, end);
    }

    @PutMapping("/{deviceCode}/{pointCode}/threshold")
    public ResponseEntity<Void> threshold(@PathVariable String deviceCode,
                                          @PathVariable String pointCode,
                                          @RequestBody ThresholdReq req,
                                          @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        pointService.updateThreshold(user, deviceCode, pointCode, req.min(), req.max());
        return ResponseEntity.ok().build();
    }
    record ThresholdReq(Double min, Double max){}

    // 单独修改物理量程
    @PutMapping("/{deviceCode}/{pointCode}/range")
    public ResponseEntity<Void> updateRange(@PathVariable String deviceCode,
                                            @PathVariable String pointCode,
                                            @RequestBody RangeReq req,
                                            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        pointService.updateRange(user, deviceCode, pointCode, req.min(), req.max());
        return ResponseEntity.ok().build();
    }
    record RangeReq(Double min, Double max){}

    // 新增测点
    @PostMapping("/{deviceCode}")
    public ResponseEntity<Void> create(@PathVariable String deviceCode,
                                       @RequestBody PointCommand cmd,
                                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        pointService.create(user, deviceCode, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 修改测点
    @PutMapping("/{deviceCode}/{pointCode}")
    public ResponseEntity<Void> update(@PathVariable String deviceCode,
                                       @PathVariable String pointCode,
                                       @RequestBody PointCommand cmd,
                                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        pointService.update(user, deviceCode, pointCode, cmd);
        return ResponseEntity.ok().build();
    }

    // 删除测点（逻辑删除）
    @DeleteMapping("/{deviceCode}/{pointCode}")
    public ResponseEntity<Void> delete(@PathVariable String deviceCode,
                                       @PathVariable String pointCode,
                                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        pointService.delete(user, deviceCode, pointCode);
        return ResponseEntity.noContent().build();
    }
}
