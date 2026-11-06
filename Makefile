.PHONY: help setup dev dev-verbose dev-logs install lint typecheck test check-all fix clean

help:
	@echo "Available targets:"
	@echo ""
	@echo "Main workflow:"
	@echo "  dev          - Run all checks with clean summary (recommended)"
	@echo "  dev-verbose  - Same as dev but with full detailed output"
	@echo "  dev-logs     - View detailed logs from last 'make dev' run"
	@echo ""
	@echo "Individual checks:"
	@echo "  lint         - Run code linter"
	@echo "  fix          - Auto-fix linting and formatting issues"
	@echo "  typecheck    - Run type checker"
	@echo "  test         - Run tests with coverage"
	@echo "  check-all    - Run all quality checks (verbose)"
	@echo ""
	@echo "Setup & maintenance:"
	@echo "  setup        - First-time setup (venv, deps, hooks)"
	@echo "  install      - Install/update dependencies"
	@echo "  clean        - Remove cache files and logs"
	@echo ""
	@echo "💡 Tip: Start with 'make dev' - it shows a clean summary with hints"
	@echo "   on how to drill down into any issues found."

setup:
	@echo "==> Setting up development environment..."
	@if [ ! -d .venv ]; then \
		echo "Creating virtual environment..."; \
		python3 -m venv .venv; \
	fi
	@echo "Activating environment and installing dependencies..."
	@. .venv/bin/activate && pip install --upgrade pip && pip install -r requirements.txt
	@if [ -f requirements-dev.txt ]; then \
		. .venv/bin/activate && pip install -r requirements-dev.txt; \
	fi
	@if [ -f scripts/setup-hooks.sh ]; then \
		echo "Configuring git hooks..."; \
		./scripts/setup-hooks.sh; \
	fi
	@echo ""
	@echo "✅ Setup complete!"
	@echo ""
	@echo "Next steps:"
	@echo "  1. Activate environment: source .venv/bin/activate"
	@echo "  2. Set PYTHONPATH:       source set_env.sh  (if exists)"
	@echo "  3. Run dev workflow:     make dev"

dev:
	@if [ ! -d .venv ]; then \
		echo "❌ Virtual environment not found!"; \
		echo ""; \
		echo "Please run: make setup"; \
		echo ""; \
		exit 1; \
	fi
	@mkdir -p .make-logs
	@TIMESTAMP=$$(date +%Y%m%d-%H%M%S); \
	echo "🚀 Running development workflow..."; \
	echo ""; \
	printf "📦 Installing dependencies..."; \
	. .venv/bin/activate && pip install -r requirements.txt > .make-logs/$$TIMESTAMP-install.log 2>&1 && \
	if [ -f requirements-dev.txt ]; then \
		pip install -r requirements-dev.txt >> .make-logs/$$TIMESTAMP-install.log 2>&1; \
	fi && echo " done" || echo " failed"; \
	printf "🔍 Linting code..."; \
	if . .venv/bin/activate && python3 -m ruff --version >/dev/null 2>&1; then \
		. .venv/bin/activate && python3 -m ruff check src/ tests/ > .make-logs/$$TIMESTAMP-lint.log 2>&1 && echo " done" || echo " done"; \
	else \
		echo " skipped (ruff not installed)"; \
	fi; \
	printf "🎯 Type checking..."; \
	if . .venv/bin/activate && python3 -m mypy --version >/dev/null 2>&1; then \
		. .venv/bin/activate && python3 -m mypy src/ > .make-logs/$$TIMESTAMP-mypy.log 2>&1 || true; \
		echo " done"; \
	else \
		echo " skipped (mypy not installed)"; \
	fi; \
	printf "🧪 Running tests..."; \
	if . .venv/bin/activate && python3 -m pytest --version >/dev/null 2>&1; then \
		if [ -d tests ]; then \
			. .venv/bin/activate && python3 -m pytest tests/ -v --cov=src --cov-report=term-missing --cov-report=html > .make-logs/$$TIMESTAMP-pytest.log 2>&1 && echo " done" || echo " done"; \
		elif [ -d test ]; then \
			. .venv/bin/activate && python3 -m pytest test/ -v --cov=src --cov-report=term-missing --cov-report=html > .make-logs/$$TIMESTAMP-pytest.log 2>&1 && echo " done" || echo " done"; \
		else \
			echo " skipped (no test directory found)"; \
		fi; \
	else \
		echo " skipped (pytest not installed)"; \
	fi; \
	echo ""; \
	if [ -f scripts/format_dev_output.py ]; then \
		. .venv/bin/activate && python3 scripts/format_dev_output.py; \
	else \
		echo "✅ Development workflow complete!"; \
		echo ""; \
		echo "💡 For detailed output: make dev-logs"; \
	fi

dev-verbose: install check-all
	@echo ""
	@echo "✅ Development workflow complete - ready to code!"

dev-logs:
	@if [ -d .make-logs ]; then \
		LATEST=$$(ls -t .make-logs/*-install.log 2>/dev/null | head -1 | sed 's/-install.log//'); \
		if [ -n "$$LATEST" ]; then \
			echo "==> Logs from: $$(basename $$LATEST)"; \
			echo ""; \
			for log in $$LATEST-*.log; do \
				if [ -f "$$log" ]; then \
					echo "==> $$(basename $$log):"; \
					cat "$$log"; \
					echo ""; \
				fi; \
			done; \
		else \
			echo "No logs found - run 'make dev' first"; \
		fi; \
	else \
		echo "No logs found - run 'make dev' first"; \
	fi

install:
	@if [ ! -d .venv ]; then \
		echo "❌ Virtual environment not found! Run: make setup"; \
		exit 1; \
	fi
	@echo "Installing dependencies..."
	@. .venv/bin/activate && pip install -r requirements.txt
	@if [ -f requirements-dev.txt ]; then \
		echo "Installing development dependencies..."; \
		. .venv/bin/activate && pip install -r requirements-dev.txt; \
	fi
	@echo "✅ Dependencies installed"

lint:
	@if [ ! -d .venv ]; then \
		echo "❌ Virtual environment not found! Run: make setup"; \
		exit 1; \
	fi
	@if . .venv/bin/activate && python3 -m ruff --version >/dev/null 2>&1; then \
		echo "Running ruff linter..."; \
		. .venv/bin/activate && python3 -m ruff check src/ tests/ || python3 -m ruff check src/ test/ || true; \
	else \
		echo "⚠️  Ruff not installed - install with: pip install ruff"; \
	fi

typecheck:
	@if [ ! -d .venv ]; then \
		echo "❌ Virtual environment not found! Run: make setup"; \
		exit 1; \
	fi
	@if . .venv/bin/activate && python3 -m mypy --version >/dev/null 2>&1; then \
		echo "Running mypy type checker..."; \
		. .venv/bin/activate && python3 -m mypy src/ || true; \
	else \
		echo "⚠️  Mypy not installed - install with: pip install mypy"; \
	fi

test:
	@if [ ! -d .venv ]; then \
		echo "❌ Virtual environment not found! Run: make setup"; \
		exit 1; \
	fi
	@if . .venv/bin/activate && python3 -m pytest --version >/dev/null 2>&1; then \
		if [ -d tests ]; then \
			. .venv/bin/activate && python3 -m pytest tests/ -v --cov=src --cov-report=term-missing --cov-report=html; \
		elif [ -d test ]; then \
			. .venv/bin/activate && python3 -m pytest test/ -v --cov=src --cov-report=term-missing --cov-report=html; \
		else \
			echo "⚠️  No test directory found (tests/ or test/)"; \
		fi; \
	else \
		echo "⚠️  Pytest not installed - install with: pip install pytest pytest-cov"; \
	fi

check-all: lint typecheck test
	@echo ""
	@echo "✅ All quality checks complete!"

fix:
	@if [ ! -d .venv ]; then \
		echo "❌ Virtual environment not found! Run: make setup"; \
		exit 1; \
	fi
	@if . .venv/bin/activate && python3 -m ruff --version >/dev/null 2>&1; then \
		echo "Auto-fixing linting and formatting issues..."; \
		. .venv/bin/activate && python3 -m ruff check --fix src/ tests/ || python3 -m ruff check --fix src/ test/ || true; \
		. .venv/bin/activate && python3 -m ruff format src/ tests/ || python3 -m ruff format src/ test/ || true; \
		echo "✅ Auto-fix complete"; \
	else \
		echo "⚠️  Ruff not installed - install with: pip install ruff"; \
	fi

clean:
	@echo "Cleaning up cache files and logs..."
	@find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	@find . -type d -name ".pytest_cache" -exec rm -rf {} + 2>/dev/null || true
	@find . -type d -name ".ruff_cache" -exec rm -rf {} + 2>/dev/null || true
	@find . -type d -name ".mypy_cache" -exec rm -rf {} + 2>/dev/null || true
	@find . -type d -name "*.egg-info" -exec rm -rf {} + 2>/dev/null || true
	@find . -type f -name "*.pyc" -delete 2>/dev/null || true
	@rm -rf htmlcov/ .coverage 2>/dev/null || true
	@rm -rf .make-logs/ 2>/dev/null || true
	@echo "✅ Cleanup complete"
