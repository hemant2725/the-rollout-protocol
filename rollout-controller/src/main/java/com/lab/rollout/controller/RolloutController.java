package com.lab.rollout.controller;

import com.lab.rollout.service.RolloutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RolloutController {

    private final RolloutService rolloutService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        log.info("GET /health requested");
        return Map.of(
            "status", "UP",
            "service", "rollout-controller",
            "currentCanaryPercent", rolloutService.getCurrentCanaryPercent(),
            "lastAction", rolloutService.getLastAction()
        );
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        log.info("GET /status requested");
        return Map.of(
            "currentCanaryPercent", rolloutService.getCurrentCanaryPercent(),
            "lastAction", rolloutService.getLastAction(),
            "lastActionTime", rolloutService.getLastActionTime().toString()
        );
    }
}
