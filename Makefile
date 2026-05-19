.PHONY: help diagrams diagrams-svg diagrams-png

help:
	@echo "Available targets:"
	@echo "  make diagrams      — generate SVG + PNG for all .mmd files"
	@echo "  make diagrams-svg  — generate SVG only (vector, zero blur)"
	@echo "  make diagrams-png  — generate PNG only (scale=5, ~300 DPI)"

diagrams:
	@bash scripts/generate-diagrams.sh

diagrams-svg:
	@bash scripts/generate-diagrams.sh --svg-only

diagrams-png:
	@bash scripts/generate-diagrams.sh --png-only
