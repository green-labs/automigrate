# Styling for output
YELLOW := "\e[1;33m"
NC := "\e[0m"
INFO := @sh -c '\
    printf $(YELLOW); \
    echo "=> $$1"; \
    printf $(NC)' VALUE


DIRS?=src test build.clj
GOALS = $(filter-out $@,$(MAKECMDGOALS))

.SILENT:  # Ignore output of make `echo` command


.PHONY: help  # Show list of targets with descriptions
help:
	@$(INFO) "Commands:"
	@grep '^.PHONY: .* #' Makefile | sed 's/\.PHONY: \(.*\) # \(.*\)/\1 > \2/' | column -tx -s ">"


.PHONY: deps  # Install deps
deps:
	@$(INFO) "Install deps..."
	@clojure -P -X:test:dev:outdated


.PHONY: repl  # Running repl
repl:
	@$(INFO) "Running repl..."
	@clj -A:test:dev


.PHONY: lint  # Linting code
lint:
	@$(INFO) "Linting project..."
	@clj-kondo --parallel --lint $(DIRS)


.PHONY: lint-init  # Linting code with libraries
lint-init:
	@$(INFO) "Linting project's classpath..."
	@clj-kondo --parallel --dependencies --copy-configs --lint $(shell clj -Spath)


.PHONY: outdated  # Check deps versions
outdated:
	@$(INFO) "Checking deps versions..."
	@clojure -M:outdated

.PHONY: outdated-fix  # Check deps versions and upgrade
outdated-fix:
	@$(INFO) "Upgrading deps versions..."
	@clojure -M:outdated --upgrade --force

.PHONY: check  # Check linting and apply formatting locally
check:
	@$(MAKE) fmt
	@$(MAKE) lint
	@$(MAKE) test


.PHONY: test  # Run tests
test:
	@$(INFO) "Running tests..."
	@clojure -X:dev:test

.PHONY: test-ci  # Run tests in CI in fail fast mode
test-ci:
	@$(INFO) "Running tests..."
	@clojure -X:dev:test :fail-fast? true

# Docker compose

.PHONY: up  # Run db, testing db and db admin web UI locally for development
up:
	@$(INFO) "Running db..."
	@docker compose up -d db test-postgres


# Testing commands

.PHONY: migrations  # Making migrations
migrations:
	@clojure -A:dev -X:migrations $(GOALS)


# Build and release

.PHONY: install-snapshot  # Build and install snapshot of package with next version locally
install-snapshot:
	@$(INFO) "Installing a jar locally..."
	@clojure -T:build install :snapshot? true :bump $(GOALS)


.PHONY: deploy-snapshot  # Build and deploy snapshot of package with next version to Clojars from local machine
deploy-snapshot:
	@$(INFO) "Deploying jar-file to Clojars..."
	@clojure -T:build deploy :snapshot? true :bump $(GOALS)


.PHONY: deploy-ci  # Build and deploy latest version of package to Clojars in CI
deploy-ci:
	@$(INFO) "Deploying jar-file to Clojars..."
	@clojure -T:build deploy :release? true


.PHONY: next-changelog  # Generate draft of changelog for next release. Need to correct by hand!
next-changelog:
	@npx auto-changelog --output NEXT-CHANGELOG.md --template keepachangelog


.PHONY: release  # Bump tag version and push it to remote repo
release:
	@$(INFO) "Deploying jar-file to Clojars..."
	@clojure -T:build release :bump $(GOALS)
