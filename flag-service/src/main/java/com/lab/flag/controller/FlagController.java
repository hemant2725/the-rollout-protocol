package com.lab.flag.controller;

import com.lab.flag.model.FeatureFlag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/flags")
public class FlagController {

    private final Map<String, FeatureFlag> flags = new ConcurrentHashMap<>();

    public FlagController() {
        flags.put("fraud-model-v2", new FeatureFlag("fraud-model-v2", false, 0));
        log.info("Flag service initialized with default flag: fraud-model-v2 (enabled=false, rolloutPercent=0)");
    }

    @GetMapping("/{name}")
    public FeatureFlag getFlag(@PathVariable String name) {
        FeatureFlag flag = flags.getOrDefault(name, new FeatureFlag(name, false, 0));
        log.info("GET /flags/{} → enabled={}, rolloutPercent={}", name, flag.isEnabled(), flag.getRolloutPercent());
        return flag;
    }

    @PostMapping("/{name}/enable")
    public FeatureFlag enableFlag(@PathVariable String name) {
        FeatureFlag flag = flags.computeIfAbsent(name, k -> new FeatureFlag(k, false, 0));
        flag.setEnabled(true);
        log.info("Flag '{}' enabled", name);
        return flag;
    }

    @PostMapping("/{name}/disable")
    public FeatureFlag disableFlag(@PathVariable String name) {
        FeatureFlag flag = flags.computeIfAbsent(name, k -> new FeatureFlag(k, false, 0));
        flag.setEnabled(false);
        flag.setRolloutPercent(0);
        log.info("Flag '{}' disabled, rollout reset to 0%", name);
        return flag;
    }

    @PostMapping("/{name}/rollout")
    public FeatureFlag setRollout(@PathVariable String name, @RequestParam int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        FeatureFlag flag = flags.computeIfAbsent(name, k -> new FeatureFlag(k, false, 0));
        flag.setRolloutPercent(clamped);
        log.info("Flag '{}' rollout set to {}%", name, clamped);
        return flag;
    }

}
