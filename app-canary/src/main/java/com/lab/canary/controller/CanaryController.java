package com.lab.canary.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Random;

@Slf4j
@RestController
public class CanaryController {

    @Value("${flag.service.url}")
    private String flagServiceUrl;

    @Value("${SIMULATE_ERRORS:false}")
    private boolean simulateErrors;

    private final WebClient webClient = WebClient.builder()
        .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build();

    private final Random random = new Random();

    @GetMapping("/process")
    public Map<String, String> process() {
        if (simulateErrors && random.nextDouble() < 0.9) {
            log.error("Canary v2 simulated error - returning 500");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Simulated canary error");
        }

        boolean flagEnabled = checkFlagService();

        if (flagEnabled) {
            log.info("Canary v2 processing request with fraud model enabled");
            return Map.of(
                "version", "v2",
                "result", "processed by canary with fraud model",
                "status", "ok"
            );
        } else {
            log.info("Canary v2 processing request (flag off)");
            return Map.of(
                "version", "v2",
                "result", "processed by canary (flag off)",
                "status", "ok"
            );
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        double errorRate = simulateErrors ? 0.9 : 0.0;
        return Map.of(
            "status", "UP",
            "version", "v2",
            "errorRate", errorRate
        );
    }

    private boolean checkFlagService() {
        try {
            Map<String, Object> response = webClient.get()
                .uri(flagServiceUrl + "/flags/fraud-model-v2")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Flag service unreachable, defaulting to flag disabled: {}", e.getMessage());
                    return Mono.just(Map.of("enabled", false));
                })
                .block();

            return Boolean.TRUE.equals(response.get("enabled"));
        } catch (Exception e) {
            log.warn("Flag service unreachable, defaulting to flag disabled: {}", e.getMessage());
            return false;
        }
    }
}