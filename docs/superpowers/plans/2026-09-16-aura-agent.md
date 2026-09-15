# AURA Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a native Kotlin/Jetpack Compose Android assistant with a premium UI and safe phone/file/app actions.

**Architecture:** Compose UI calls small action modules that use Android intents and scoped storage APIs. A command router maps natural-language requests to safe actions and requires confirmation for destructive operations.

**Tech Stack:** Kotlin, Android SDK, Jetpack Compose, Material 3.

**Spec:** User-approved AURA Agent design in conversation.

## Global Constraints
- Native Android only; no AppDeploy.
- Kotlin + Jetpack Compose.
- Local-first command processing.
- Do not bypass Android security.
- Destructive actions require confirmation.

---

- [x] Create Android project scaffold.
- [x] Create premium home UI with animated orb.
- [x] Add text and voice command entry.
- [x] Add safe phone quick actions.
- [ ] Add installed-app discovery and launcher.
- [ ] Add file-agent operations with confirmation.
- [ ] Add Permission Center and command router.
- [ ] Add automated UI/unit tests.
