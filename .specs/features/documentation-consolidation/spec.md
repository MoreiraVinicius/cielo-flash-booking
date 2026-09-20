# Documentation Consolidation Specification

**Status:** Implementing

## Goal

Make `.specs/` the only authoritative location for the current system's behavior, architecture, decisions, plans, and verification. Keep repository documentation only when it provides an original input, an executable operational procedure, or a concise navigation entry point.

## Problem Statement

The repository has parallel plans, ADRs, evaluation matrices, model descriptions, and local setup guides outside `.specs/`. Some repeat current specifications and one parallel plan already diverges from the current high-load task count. Historical AWS material is also presented beside current operating instructions.

## Assumptions & Open Questions

- The user approved removal of duplicated and historical Markdown identified in the documentation review.
- The original case, operational guides, Postman instructions, performance provenance, and Terraform module READMEs remain useful when they do not duplicate specifications.
- Generated interview audio and its generated input transcript are not versioned; the reusable PowerShell generator is versioned.
- No open product or architecture decision is required for this documentation-only change.

## User Stories

- As an engineer, I can find the current system intent in `.specs/` without reconciling copies.
- As an interviewer, I can use README and operational guides without mistaking historical plans for active work.
- As a contributor, I can generate interview audio from any local UTF-8 text file without committing generated media.

## Requirements

### DOC-01 - Authoritative documentation

WHEN an engineer needs the current implementation intent, architecture, decision, task, or validation result, THEN `.specs/` is the sole authoritative source.

### DOC-02 - Non-spec documentation

WHEN a Markdown file outside `.specs/` repeats specification content, THEN it is removed or reduced to a link to the corresponding specification.

### DOC-03 - Operational material

WHEN a document contains an executable local procedure, Terraform-module context, Postman usage, performance provenance, or the original case, THEN it remains only if it does not restate the system specification.

### DOC-04 - Interview audio utility

WHEN the interview audio generator is versioned, THEN it accepts caller-provided text and does not require generated audio or a generated script as a repository input.

### DOC-05 - Navigation integrity

WHEN the consolidation is complete, THEN README and retained Markdown links resolve to retained documents or `.specs/` sources.

## Scope

- Move the project glossary into `.specs/`.
- Replace duplicated narrative with links to specifications.
- Keep a single local execution guide.
- Remove generated, temporary, duplicated, superseded, and unreferenced documentation.
- Keep the audio generation script as a generic utility, not generated audio assets.

## Out of Scope

- Changing Java behavior, Terraform resources, API contracts, or test coverage.
- Re-provisioning the historical AWS demo.

## Requirement Traceability

| Requirement | Verification |
| --- | --- |
| DOC-01 | `.specs/STATE.md` records the source-of-truth decision and the glossary resides in `.specs/`. |
| DOC-02 | Duplicate plans, ADRs, model, evaluation, historical planning, prompts, and duplicate IDE guide are absent. |
| DOC-03 | Retained README, local guide, Terraform READMEs, Postman and performance documentation contain operational navigation or provenance. |
| DOC-04 | `scripts/generate-interview-audio.ps1` parses with a caller-provided input path and generated audio assets are absent. |
| DOC-05 | Local Markdown link validation reports no missing retained targets. |
