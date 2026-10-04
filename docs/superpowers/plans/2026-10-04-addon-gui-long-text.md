# Addon GUI Long Text Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for each repository; independent file groups within a repository may use superpowers:dispatching-parallel-agents.

**Goal:** Fixed-space authored text scrolls inside its own bounds for arbitrary English/Chinese translations; neighboring controls stay clear.
**Architecture:** Use existing KineticGraphics scrollingText/Centered/Right; widths come from layout boundaries. Wrapped descriptions reserve KineticText.wrappedHeight. Do not introduce scrolling implementations, loader branches or shortened translations.
**Tech Stack:** Existing addon builds, Java, KineticCore GUI API.
**Spec:** D:/IDEAWork/KineticCore/docs/Addon-GUI-Long-Text-Handoff.md (user provided).

## Global constraints

Core, TextStudio and RealmControl are read-only. Preserve preexisting changes. HUD/world text, bounded numbers and wrapped lines are excluded unless actual overlaps are found. The user explicitly included tooltip overflow. Source language keys remain identical. Local commits only, normal local Git identity, no AI attribution; commit messages English first then Chinese.
Use existing game installations and memory settings, avoiding desktop input and game downloads. Only normally stop the test client launched for this task; do not stop user instances.

## Repository sequence

Complete, validate and commit one repository before editing the next:
1. ContentStudio.
2. AdventureSystems.
3. EntityControl.
4. ItemControl.
5. KineticArmory.
6. CombatSystems.
7. TACZWorkshop.
8. MobAscension.
9. ModRefinery.

## ContentStudio

- [x] Record clean/dirty baseline and verify scrolling API in actual dependencies.
- [x] Review villager fixed labels, list columns, titles and trade summaries; calculate limits before controls.
- [x] Review loot pages, tables, previews and item component editors with the same boundary rules.
- [x] Review recipe pages and indirect rendering helpers; use the shared wrapping tooltip API for the observed overflow.
- [x] Fresh review of all changes and remaining direct text calls.
- [x] buildAll --offline passes Forge and NeoForge including architecture, references, Mixin targets and language parity.
- [x] Real existing 1.21.1 client: English at 854x480 and enlarged, automatic GUI scale; every changed page; Chinese recheck.
- [x] Record any unverified cases truthfully, update bilingual CHANGELOG and verification report.
- [x] Commit only this repository before proceeding.

Apply equivalent per-project checks to the remaining repositories; Forge-only projects use existing build commands and existing Forge profile.

ContentStudio validation limits: populated global-removal and exclusion rows were replaced by the server response before capture; those row bounds were reviewed in source. Tall tooltip geometry still fails in Core 26.10.4 and is handed off separately; it is not counted as a fixed addon issue.
