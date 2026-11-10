package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.PointValue;
import com.donghuan.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
}
