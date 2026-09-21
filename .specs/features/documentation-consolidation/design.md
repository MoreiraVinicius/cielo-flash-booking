# Documentation Consolidation Design

## Canonical Layout

`.specs/` contains the current system specification, design, tasks, decisions and validation. `README.md` is a short entry point. Documentation outside `.specs/` is retained only for one of these purposes:

- the original interview case;
- an executable local or operational procedure;
- a tool-specific guide or performance provenance;
- a Terraform module interface.

The shared terminology is in `.specs/CONTEXT.md`, and architectural decisions are in `.specs/STATE.md`. Retained guides link to those sources instead of copying them.

## Cleanup Rules

- Remove legacy plans, ADR files, duplicated architecture/model/evaluation narratives, generated media and empty directories.
- Keep only `docs/rodar-localmente.md` as the local execution guide.
- Keep `scripts/generate-interview-audio.ps1` as a generic utility: it receives an input file and never depends on a tracked generated transcript or audio asset.
- Update references before removing a target, then validate all local Markdown links.

## Boundaries

This change reorganizes documentation only. It does not alter Java behavior, Terraform resources, API contracts or test results.
