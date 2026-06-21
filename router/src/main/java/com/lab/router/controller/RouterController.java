package com.lab.router.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
public class RouterController {

    @Value("${stable.service.url}")
    private String stableUrl;

    @Value("${canary.service.url}")
    private String canaryUrl;

    @Value("${flag.service.url}")
    private String flagServiceUrl;

    private final WebClient webClient = WebClient.builder()
        .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build();

    private final Random random = new Random();
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger stableCount = new AtomicInteger(0);
    private final AtomicInteger canaryCount = new AtomicInteger(0);

    @GetMapping("/route")
    public Map<String, Object> route() {
        int canaryPercent = getCanaryPercent();
        int roll = random.nextInt(100);
        boolean sendToCanary = roll < canaryPercent;

        totalRequests.incrementAndGet();
        Map<String, Object> response;

        if (sendToCanary) {
            canaryCount.incrementAndGet();
            log.info("Routing to CANARY (roll={}/{}, canaryPercent={})", roll, canaryPercent, canaryPercent);
            response = callService(canaryUrl, "canary");
        } else {
            stableCount.incrementAndGet();
            log.info("Routing to STABLE (roll={}/{}, canaryPercent={})", roll, canaryPercent, canaryPercent);
            response = callService(stableUrl, "stable");
        }

        response.put("routedTo", sendToCanary ? "canary" : "stable");
        response.put("canaryPercent", canaryPercent);
        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> stableHealth = callHealth(stableUrl, "stable");
        Map<String, Object> canaryHealth = callHealth(canaryUrl, "canary");

        boolean stableUp = "UP".equals(stableHealth.get("status"));
        boolean canaryUp = "UP".equals(canaryHealth.get("status"));

        String overallStatus = (stableUp || canaryUp) ? "UP" : "DOWN";

        return Map.of(
            "status", overallStatus,
            "stable", stableHealth,
            "canary", canaryHealth
        );
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        int canaryPercent = getCanaryPercent();
        return Map.of(
            "totalRequests", totalRequests.get(),
            "stableCount", stableCount.get(),
            "canaryCount", canaryCount.get(),
            "canaryPercent", canaryPercent
        );
    }

    private int getCanaryPercent() {
        try {
            Map<String, Object> response = webClient.get()
                .uri(flagServiceUrl + "/flags/fraud-model-v2")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Flag service unreachable, defaulting canaryPercent to 0: {}", e.getMessage());
                    return Mono.just(Map.of("rolloutPercent", 0));
                })
                .block();

            Object rollout = response.get("rolloutPercent");
            if (rollout instanceof Number) {
                return ((Number) rollout).intValue();
            }
            return 0;
        } catch (Exception e) {
            log.warn("Error fetching canary percent, defaulting to 0: {}", e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callService(String url, String serviceName) {
        try {
            return webClient.get()
                .uri(url + "/process")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("{} service unreachable, returning fallback: {}", serviceName, e.getMessage());
                    return Mono.just(Map.of(
                        "version", "fallback",
                        "result", serviceName + " is unavailable",
                        "status", "DOWN"
                    ));
                })
                .block();
        } catch (Exception e) {
            log.warn("{} service unreachable, returning fallback: {}", serviceName, e.getMessage());
            return Map.of(
                "version", "fallback",
                "result", serviceName + " is unavailable",
                "status", "DOWN"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callHealth(String url, String serviceName) {
        try {
            return webClient.get()
                .uri(url + "/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("{} health check failed: {}", serviceName, e.getMessage());
                    return Mono.just(Map.of("status", "DOWN", "version", "unknown", "errorRate", 1.0));
                })
                .block();
        } catch (Exception e) {
            log.warn("{} health check failed: {}", serviceName, e.getMessage());
            return Map.of("status", "DOWN", "version", "unknown", "errorRate", 1.0);
        }
    }
}