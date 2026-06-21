package com.lab.stable.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
public class StableController {

    @GetMapping("/process")
    public Map<String, String> process() {
        log.info("Stable v1 processing request");
        return Map.of(
            "version", "v1",
            "result", "processed by stable",
            "status", "ok"
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "version", "v1",
            "errorRate", 0.0
        );
    }
}