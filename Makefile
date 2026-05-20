COMPOSE  = docker compose -f infra/docker-compose.yml
APP_URL  = http://localhost:8081
MGMT_URL = http://localhost:9090

.DEFAULT_GOAL := help
.PHONY: help up down restart logs test ps clean diagrams diagrams-svg diagrams-png

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?##' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# ---------------------------------------------------------------------------
# Local dev — service stack
# ---------------------------------------------------------------------------

up: ## Start full stack (postgres + app), builds image if needed
	$(COMPOSE) up -d --build
	@echo "" && echo "Waiting for service to be ready..."
	@until curl -s $(MGMT_URL)/actuator/health/liveness > /dev/null 2>&1; do sleep 3; done
	@echo "" && echo "  App     → $(APP_URL)" && echo "  Swagger → $(APP_URL)/swagger-ui.html" && echo "  Metrics → $(MGMT_URL)/actuator/prometheus"

down: ## Stop and remove containers
	$(COMPOSE) down

restart: ## Rebuild app image and restart (use after code changes)
	$(COMPOSE) up -d --build transfer-service
	@echo "Waiting for restart..." && until curl -s $(MGMT_URL)/actuator/health/liveness > /dev/null 2>&1; do sleep 3; done && echo "Ready → $(APP_URL)"

logs: ## Tail transfer-service logs (Ctrl+C to stop)
	$(COMPOSE) logs -f transfer-service

ps: ## Show container status
	$(COMPOSE) ps

test: ## Run API smoke tests against localhost
	@BASE_URL=$(APP_URL) MGMT_URL=$(MGMT_URL) bash infra/smoke/smoke-test.sh

clean: ## Stop containers and wipe volumes (fresh DB next time)
	$(COMPOSE) down -v

# ---------------------------------------------------------------------------
# Diagrams
# ---------------------------------------------------------------------------

diagrams: ## Generate SVG + PNG for all .mmd files
	@bash scripts/generate-diagrams.sh

diagrams-svg: ## Generate SVG only
	@bash scripts/generate-diagrams.sh --svg-only

diagrams-png: ## Generate PNG only (scale=5, ~300 DPI)
	@bash scripts/generate-diagrams.sh --png-only
