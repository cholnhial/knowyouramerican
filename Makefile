# Know Your American — local workflows. Run `make help` for a list.
SHELL := /bin/bash
MVN   := ./mvnw
DB    ?= ./data/kya.db

.DEFAULT_GOAL := help
.PHONY: help dev run css css-watch test test-extractor extract idioms puzzle native run-native db-reset db-shell clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-12s\033[0m %s\n",$$1,$$2}'

dev: ## Tailwind --watch + Spring Boot (dev profile) → http://localhost:8080
	@./scripts/tailwind.sh >/dev/null 2>&1 || true
	@trap 'kill 0' EXIT; \
	  ./scripts/tailwind.sh --watch=always & \
	  $(MVN) -q spring-boot:run -Dtailwind.skip=true -Dspring-boot.run.profiles=dev; \
	  wait

run: ## Run the app (prod profile) on the JVM
	$(MVN) -q spring-boot:run

css: ## Build minified CSS once
	./scripts/tailwind.sh --minify

css-watch: ## Rebuild CSS on template changes
	./scripts/tailwind.sh --watch=always

test: ## Run all tests
	$(MVN) verify

test-extractor: ## Run the PDF extractor's golden tests
	$(MVN) -q -f tools/idiom-extractor/pom.xml test

extract: ## Re-extract docs/idioms.pdf → src/main/resources/db/seed/idioms.jsonl
	$(MVN) -q -f tools/idiom-extractor/pom.xml compile exec:java 2>&1 | grep -v -e 'toUnicode' -e 'No Unicode mapping'

idioms: ## Show 10 random idioms from the local DB (N=10, MIN=0 fun score)
	@sqlite3 -separator ' │ ' $(DB) "SELECT fun_score, headword, definition FROM idiom WHERE fun_score >= $${MIN:-0} ORDER BY random() LIMIT $${N:-10};"

puzzle: ## Print a day's questions from the local DB (DAY=2026-07-04, default: today UTC)
	@day=$${DAY:-$$(date -u +%F)}; echo "── $$day ──"; \
	sqlite3 $(DB) "SELECT q.position || '. [' || q.type || '] ' || q.prompt || char(10) || \
	  (SELECT group_concat(CASE WHEN o.key = q.correct_index THEN '     ✓ ' ELSE '       ' END || o.value, char(10)) \
	   FROM json_each(q.options_json) o) \
	  FROM question q WHERE q.day_date = '$$day' ORDER BY q.position;"

native: ## Compile the GraalVM native binary → target/knowyouramerican (needs GraalVM ≥ 25.0.2)
	$(MVN) -Pnative clean native:compile -DskipTests

run-native: ## Run the native binary against $(DB)
	KYA_DB_PATH=$(DB) ./target/knowyouramerican

db-reset: ## Delete the local DB (next start re-migrates, reseeds, backfills)
	rm -f $(DB) $(DB)-wal $(DB)-shm
	@echo "Deleted $(DB)"

db-shell: ## Open the local DB in sqlite3
	sqlite3 $(DB)

clean: ## Remove build output
	$(MVN) -q clean
