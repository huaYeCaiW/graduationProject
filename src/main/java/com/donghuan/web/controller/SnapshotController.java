package com.donghuan.web.controller;

import com.donghuan.model.PointValue;
import com.donghuan.store.PointSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {
    private final PointSnapshot pointSnapshot;

    @GetMapping
    public Map<String, PointValue> snapshots() {
        return pointSnapshot.findAllSnapshots();
    }
}
