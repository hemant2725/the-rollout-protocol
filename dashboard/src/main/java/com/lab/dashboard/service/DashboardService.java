package com.lab.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class DashboardService {

    @Value("${router.service.url}")
    private String routerUrl;

    @Value("${flag.service.url}")
    private String flagUrl;

    @Value("${stable.service.url}")
    private String stableUrl;

    @Value("${canary.service.url}")
    private String canaryUrl;

    @Value("${rollout.service.url}")
    private String rolloutUrl;

    private final WebClient webClient = WebClient.builder()
        .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build();

    private final AtomicReference<Map<String, Object>> cachedData = new AtomicReference<>(new ConcurrentHashMap<>());

    @Scheduled(fixedRate = 2000)
    public void refreshData() {
        try {
            Map<String, Object> routerHealth = fetch(routerUrl + "/health");
            Map<String, Object> routerStats = fetch(routerUrl + "/stats");
            Map<String, Object> flagHealth = fetch(flagUrl + "/health");
            Map<String, Object> flagData = fetch(flagUrl + "/flags/fraud-model-v2");
            Map<String, Object> stableHealth = fetch(stableUrl + "/health");
            Map<String, Object> canaryHealth = fetch(canaryUrl + "/health");
            Map<String, Object> rolloutHealth = fetch(rolloutUrl + "/health");
            Map<String, Object> rolloutStatus = fetch(rolloutUrl + "/status");

            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("routerHealth", routerHealth);
            data.put("routerStats", routerStats);
            data.put("flagHealth", flagHealth);
            data.put("flagData", flagData);
            data.put("stableHealth", stableHealth);
            data.put("canaryHealth", canaryHealth);
            data.put("rolloutHealth", rolloutHealth);
            data.put("rolloutStatus", rolloutStatus);
            data.put("timestamp", System.currentTimeMillis());

            cachedData.set(data);
        } catch (Exception e) {
            log.warn("Dashboard refresh failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetch(String url) {
        try {
            return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch {}: {}", url, e.getMessage());
                    return Mono.just(Map.of("status", "DOWN", "error", e.getMessage()));
                })
                .block();
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    public Map<String, Object> getDashboardData() {
        return cachedData.get();
    }
}
