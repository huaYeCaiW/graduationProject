package com.donghuan.web.controller;

import com.donghuan.model.PointValue;
import com.donghuan.store.PointStore;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointStore pointStore;

    @GetMapping("/{deviceId}/{pointId}/latest")
    public ResponseEntity<?> latest(@PathVariable String deviceId, @PathVariable String pointId) {
        return pointStore.findLatest(deviceId, pointId).map(ResponseEntity :: ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{deviceId}/{pointId}/range")
    public List<PointValue> range(@PathVariable String deviceId,
                                  @PathVariable String pointId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end
                                  ) {
        return pointStore.findByRange(deviceId, pointId,start, end);
    }

    @PutMapping("/{deviceId}/{pointId}/threshold")
    public ResponseEntity<Void> threshold(@PathVariable String deviceId,
                                            @PathVariable String pointId,
                                            @RequestBody ThresholdReq req) {
        boolean ok = pointStore.updateThreshold(deviceId, pointId, req.min(), req.max());
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok().build();
    }
    record ThresholdReq(Double min, Double max){}
}
