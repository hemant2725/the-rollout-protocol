package com.lab.rollout.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class RolloutService {

    @Value("${router.service.url}")
    private String routerUrl;

    @Value("${flag.service.url}")
    private String flagServiceUrl;

    private final WebClient webClient = WebClient.builder()
        .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build();

    private int currentCanaryPercent = 0;
    private String lastAction = "initialized";
    private Instant lastActionTime = Instant.now();
    private final int[] promotionSteps = {1, 5, 25, 50, 100};

    @Scheduled(fixedRate = 5000)
    public void evaluateRollout() {
        try {
            Map<String, Object> routerHealth = getRouterHealth();
            if (routerHealth == null) {
                log.warn("Router unreachable, skipping rollout evaluation cycle");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> canaryHealth = (Map<String, Object>) routerHealth.get("canary");
            if (canaryHealth == null) {
                log.warn("Canary health data missing, skipping rollout evaluation");
                return;
            }

            Object errorRateObj = canaryHealth.get("errorRate");
            double errorRate = 0.0;
            if (errorRateObj instanceof Number) {
                errorRate = ((Number) errorRateObj).doubleValue();
            }

            log.info("Rollout evaluation: canaryPercent={}, canaryErrorRate={}", currentCanaryPercent, errorRate);

            if (errorRate >= 0.5) {
                performRollback();
            } else if (errorRate < 0.1 && currentCanaryPercent < 100) {
                performPromotion();
            }
        } catch (Exception e) {
            log.error("Error during rollout evaluation: {}", e.getMessage(), e);
        }
    }

    private void performPromotion() {
        int nextPercent = getNextPromotionStep();
        if (nextPercent > currentCanaryPercent) {
            currentCanaryPercent = nextPercent;
            setCanaryPercent(currentCanaryPercent);
            lastAction = "PROMOTED to " + currentCanaryPercent + "%";
            lastActionTime = Instant.now();
            log.warn("PROMOTION: Canary promoted to {}%", currentCanaryPercent);
        }
    }

    private void performRollback() {
        currentCanaryPercent = 0;
        setCanaryPercent(0);
        disableFlag();
        lastAction = "ROLLBACK - canaryPercent reset to 0%, flag disabled";
        lastActionTime = Instant.now();
        log.warn("ROLLBACK: Canary rolled back to 0%, fraud-model-v2 flag disabled");
    }

    private int getNextPromotionStep() {
        for (int step : promotionSteps) {
            if (step > currentCanaryPercent) {
                return step;
            }
        }
        return 100;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getRouterHealth() {
        try {
            return webClient.get()
                .uri(routerUrl + "/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Router health check failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
        } catch (Exception e) {
            log.warn("Router health check failed: {}", e.getMessage());
            return null;
        }
    }

    private void setCanaryPercent(int percent) {
        try {
            webClient.post()
                .uri(flagServiceUrl + "/flags/fraud-model-v2/rollout?percent=" + percent)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Failed to set canary percent: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
        } catch (Exception e) {
            log.warn("Failed to set canary percent: {}", e.getMessage());
        }
    }

    private void disableFlag() {
        try {
            webClient.post()
                .uri(flagServiceUrl + "/flags/fraud-model-v2/disable")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Failed to disable flag: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
        } catch (Exception e) {
            log.warn("Failed to disable flag: {}", e.getMessage());
        }
    }

    public int getCurrentCanaryPercent() {
        return currentCanaryPercent;
    }

    public String getLastAction() {
        return lastAction;
    }

    public Instant getLastActionTime() {
        return lastActionTime;
    }
}