# ADR-0001: Desktop Shell Candidate

- **Status:** Proposed; prototype required.
- **Date:** 2026-11-27.

## Context
The app must run on Windows and macOS, support mouse/keyboard and optional touch, access local services, and integrate with platform printers/filesystem.

## Decision
Evaluate Tauri 2 with React + TypeScript UI and Rust commands/plugins. Keep business logic outside the shell.

## Consequences
- Measure memory/startup/interaction on target hardware.
- Package and supervise local Spring Boot service and PostgreSQL safely.
- Test Windows signing and macOS signing/notarization.
- Verify actual thermal printer models.
- Do not finalize Tauri until prototype passes both platforms.

## Alternatives
Electron (mature ecosystem; measure runtime/memory); separate native apps (strong native integration but two UI codebases and greater maintenance).
