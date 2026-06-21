.PHONY: build up down demo-good demo-bad logs status reset help dashboard

ROUTER_URL := http://localhost:8080
FLAG_URL := http://localhost:8081
STABLE_URL := http://localhost:8082
CANARY_URL := http://localhost:8083
ROLLOUT_URL := http://localhost:8084
DASHBOARD_URL := http://localhost:8090

help:
	@echo "The Rollout Protocol - Available Commands"
	@echo "============================================"
	@echo "  build          Compile all Java services"
	@echo "  up             Start all Docker containers"
	@echo "  down           Stop all containers"
	@echo "  demo-good      Run successful rollout demo"
	@echo "  demo-bad       Run rollback demo"
	@echo "  logs           Follow all container logs"
	@echo "  status         Check health of all services"
	@echo "  reset          Disable flag, reset to 0%"
	@echo "  dashboard      Open dashboard in browser"

build:
	@echo "Building all services..."
	cd flag-service && mvn clean package -DskipTests
	cd app-stable && mvn clean package -DskipTests
	cd app-canary && mvn clean package -DskipTests
	cd router && mvn clean package -DskipTests
	cd rollout-controller && mvn clean package -DskipTests
	cd dashboard && mvn clean package -DskipTests
	@echo "All services built successfully!"

up:
	@echo "Starting all services..."
	docker-compose up --build -d
	@echo ""
	@echo "========================================"
	@echo "🚀 Dashboard is live at: $(DASHBOARD_URL)"
	@echo "========================================"
	@echo "Services starting up. Use 'make status' to check health."

down:
	@echo "Stopping all services..."
	docker-compose down
	@echo "All services stopped."

demo-good:
	@echo "============================================"
	@echo "DEMO-GOOD: Starting clean rollout scenario"
	@echo "============================================"
	export SIMULATE_ERRORS=false && docker-compose up --build -d
	@echo ""
	@echo "========================================"
	@echo "🚀 Dashboard is live at: $(DASHBOARD_URL)"
	@echo "========================================"
	@echo ""
	@echo "Waiting 20 seconds for services to start..."
	sleep 20
	@echo "Enabling fraud-model-v2 flag..."
	curl -s -X POST $(FLAG_URL)/flags/fraud-model-v2/enable | jq .
	@echo "Setting initial rollout to 1%..."
	curl -s -X POST "$(FLAG_URL)/flags/fraud-model-v2/rollout?percent=1" | jq .
	@echo ""
	@echo "Rollout controller will auto-promote every 5s if errorRate < 0.1"
	@echo "Watch promotion: watch -n 2 'curl -s $(ROLLOUT_URL)/status | jq .'"
	@echo "Watch traffic:   watch -n 1 'curl -s $(ROUTER_URL)/stats | jq .'"

demo-bad:
	@echo "============================================"
	@echo "DEMO-BAD: Starting rollback scenario"
	@echo "============================================"
	export SIMULATE_ERRORS=true && docker-compose up --build -d
	@echo ""
	@echo "========================================"
	@echo "🚀 Dashboard is live at: $(DASHBOARD_URL)"
	@echo "========================================"
	@echo ""
	@echo "Waiting 20 seconds for services to start..."
	sleep 20
	@echo "Enabling fraud-model-v2 flag..."
	curl -s -X POST $(FLAG_URL)/flags/fraud-model-v2/enable | jq .
	@echo "Setting rollout to 50%..."
	curl -s -X POST "$(FLAG_URL)/flags/fraud-model-v2/rollout?percent=50" | jq .
	@echo ""
	@echo "Canary is now simulating 90% errors!"
	@echo "Rollout controller will detect errorRate >= 0.5 and ROLLBACK"
	@echo "Watch rollback: watch -n 2 'curl -s $(ROLLOUT_URL)/status | jq .'"
	@echo "Watch flag:     watch -n 2 'curl -s $(FLAG_URL)/flags/fraud-model-v2 | jq .'"

logs:
	docker-compose logs -f

status:
	@echo "============================================"
	@echo "Health & Status Check"
	@echo "============================================"
	@echo ""
	@echo "--- Router Health ---"
	@curl -s $(ROUTER_URL)/health | jq . 2>/dev/null || echo "Router not available"
	@echo ""
	@echo "--- Router Stats ---"
	@curl -s $(ROUTER_URL)/stats | jq . 2>/dev/null || echo "Router stats not available"
	@echo ""
	@echo "--- Stable Health ---"
	@curl -s $(STABLE_URL)/health | jq . 2>/dev/null || echo "Stable not available"
	@echo ""
	@echo "--- Canary Health ---"
	@curl -s $(CANARY_URL)/health | jq . 2>/dev/null || echo "Canary not available"
	@echo ""
	@echo "--- Flag Service ---"
	@curl -s $(FLAG_URL)/flags/fraud-model-v2 | jq . 2>/dev/null || echo "Flag service not available"
	@echo ""
	@echo "--- Rollout Controller Status ---"
	@curl -s $(ROLLOUT_URL)/status | jq . 2>/dev/null || echo "Rollout controller not available"
	@echo ""
	@echo "--- Dashboard ---"
	@echo "Dashboard URL: $(DASHBOARD_URL)"
	@curl -s -o /dev/null -w "Dashboard status: %{http_code}\n" $(DASHBOARD_URL) || echo "Dashboard not available"

reset:
	@echo "============================================"
	@echo "Resetting all flags and rollout state"
	@echo "============================================"
	@echo "Disabling fraud-model-v2 flag..."
	@curl -s -X POST $(FLAG_URL)/flags/fraud-model-v2/disable | jq .
	@echo ""
	@echo "Resetting rollout to 0%..."
	@curl -s -X POST "$(FLAG_URL)/flags/fraud-model-v2/rollout?percent=0" | jq .
	@echo ""
	@echo "Reset complete. All traffic will go to stable."

dashboard:
	@echo "Opening dashboard at $(DASHBOARD_URL)"
	@open $(DASHBOARD_URL) 2>/dev/null || echo "Please open $(DASHBOARD_URL) in your browser"
