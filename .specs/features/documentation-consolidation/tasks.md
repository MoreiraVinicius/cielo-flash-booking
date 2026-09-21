# Documentation Consolidation Tasks

## Execution Protocol

Changes are documentation-only. Each task updates the source-of-truth links before its duplicated target is removed.

**Design:** `.specs/features/documentation-consolidation/design.md`  
**Status:** Complete  
**Task count:** 5

## Test Coverage Matrix

| Layer | Required Check | Location | Command |
| --- | --- | --- | --- |
| Documentation | Specification structure | `.specs/features/documentation-consolidation/` | `validate_spec.py` |
| Documentation | Local links | repository Markdown | PowerShell local-link scan |
| Script | PowerShell parse | `scripts/generate-interview-audio.ps1` | `[scriptblock]::Create(...)` |

## Gate Check Commands

| Gate | Command |
| --- | --- |
| Specification | `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_spec.py .specs\features\documentation-consolidation\spec.md` |
| Tasks | `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_tasks.py .specs\features\documentation-consolidation\tasks.md` |
| Links | PowerShell local-link scan recorded in `validation.md` |
| Script | `[scriptblock]::Create((Get-Content scripts\generate-interview-audio.ps1 -Raw))` |

## Execution Plan

```text
T01 -> T02 -> T05
T01 -> T03 -> T05
T01 -> T04 -> T05
```

## Task Breakdown

### T01: Define the canonical documentation boundary

**Status:** Complete  
**Requirement:** DOC-01  
**What:** Record `.specs/` as the current-system source and place the glossary there.  
**Where:** `.specs/STATE.md`, `.specs/CONTEXT.md`  
**Depends on:** none  
**Done when:** Current behavior, decisions and verification are discoverable from `.specs/`.  
**Tests:** Specification review  
**Gate:** Specification

### T02: Remove duplicated narrative and obsolete plans

**Status:** Complete  
**Requirement:** DOC-02  
**What:** Remove the duplicate plan, ADRs, model/evaluation narratives, historical runbook, diagram prompts and duplicate IDE guide.  
**Where:** root and `docs/`  
**Depends on:** T01  
**Done when:** Retained specifications contain no links to the removed documents.  
**Tests:** Local-link scan  
**Gate:** Links

### T03: Preserve concise operational documentation

**Status:** Complete  
**Requirement:** DOC-03  
**What:** Retain only the README entry point, local run guide and tool/provenance documentation.  
**Where:** `README.md`, `docs/rodar-localmente.md`, `postman/`, `performance/`, `infra/`  
**Depends on:** T01  
**Done when:** A reader can run locally and find tool-specific instructions without a second architecture narrative.  
**Tests:** Manual documentation review  
**Gate:** Links

### T04: Keep the audio generator reusable

**Status:** Complete  
**Requirement:** DOC-04  
**What:** Version a caller-input PowerShell audio generator and remove generated transcript/audio assets.  
**Where:** `scripts/generate-interview-audio.ps1`  
**Depends on:** T01  
**Done when:** The script parses and requires an input path.  
**Tests:** PowerShell parser  
**Gate:** Script

### T05: Verify the final documentation graph

**Status:** Complete  
**Requirement:** DOC-05  
**What:** Repoint internal references to `.specs/` and verify all local Markdown targets.  
**Where:** retained Markdown  
**Depends on:** T02, T03, T04  
**Done when:** The specification, task, link and script checks pass.  
**Tests:** Full documentation validation  
**Gate:** Links
