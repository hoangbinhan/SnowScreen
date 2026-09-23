# AGENT.md - AI Agent Development & Collaboration Guide

Welcome to the **SnowScreen** Android project repository. This project is engineered and maintained in collaboration with autonomous AI agents. This document defines the architecture, conventions, workflows, and protocols for AI agents working within this codebase.

---

## 1. Project Overview & Tech Stack

- **Project Name**: SnowScreen (Snow on Screen Pro)
- **Platform**: Android (Min SDK 24+, Target SDK 34+)
- **Language**: Kotlin 1.9+ / 2.0+
- **UI Toolkit**: Jetpack Compose, Material 3, Material You
- **Core Features**:
  - System-wide overlay service (`WindowManager` with `TYPE_APPLICATION_OVERLAY`)
  - Status Bar and Navigation Bar coverage (`FLAG_LAYOUT_IN_SCREEN`, `FLAG_LAYOUT_NO_LIMITS`)
  - Lock screen display (`FLAG_SHOW_WHEN_LOCKED`)
  - Fake 3D Parallax Depth particle engine (Multi-layer Canvas rendering at 60 FPS)
  - Sensor tilt physics (`Sensor.TYPE_ACCELEROMETER`)
  - Random wind gusts and weather presets
  - Smart Home & Lock Screen filter mode

---

## 2. Directory Structure

```tree
SnowScreen/
├── app/
│   ├── src/main/java/com/example/snowscreen/
│   │   ├── MainActivity.kt         # Jetpack Compose M3 Dashboard & Permissions
│   │   ├── model/
│   │   │   └── SnowSettings.kt     # Data classes & Weather Modes
│   │   └── service/
│   │       ├── SnowService.kt      # Foreground Service, WindowManager & Sensors
│   │       └── SnowView.kt         # 3D Parallax Particle Engine & Custom Canvas View
│   ├── src/main/AndroidManifest.xml # Permissions & Service declarations
│   └── build.gradle.kts
├── .artifacts/                     # AI planning, tasks, and walkthrough records
└── AGENT.md                        # This agent collaboration guide
```

---

## 3. AI Agent Operating Protocols

### A. Planning & Lifecycle
- For complex tasks or multi-file refactoring, agents must establish an implementation plan (`implementation_plan.artifact.md`), task checklist (`task.artifact.md`), and walkthrough (`walkthrough.artifact.md`).
- Transition states strictly through `set_plan_state` (`REFINING` ➔ `EXECUTING` ➔ `COMPLETED`).

### B. File Modification Rules (CRITICAL)
- **NEVER** use shell commands (`run_shell_command`) with `sed`, `awk`, `perl`, `cat`, or redirection (`>`, `>>`) to read or modify files.
- **ALWAYS** use built-in IDE tools:
  - `read_file` for reading file contents.
  - `write_file` for creating or replacing entire files.
  - `replace_file_content` or `multi_replace_file_content` for surgical edits.

### C. Build & Verification
- Always verify compilation after making changes by executing Gradle builds via `gradle_build` (e.g., `app:assembleDebug`).

---

## 4. Coding & Architecture Conventions

- **Idiomatic Kotlin**: Prefer immutability (`val`), data classes, coroutines, and null-safety.
- **Jetpack Compose Best Practices**: Keep composables stateless where possible, hoist state correctly, and utilize Material 3 design tokens.
- **Service & Lifecycle Management**: Ensure background foreground services are resilient, use persistent notification channels, and handle task removal cleanly (`onTaskRemoved`).
- **Performance**: Particle rendering in `SnowView` must operate within optimized frame callbacks (`Choreographer.FrameCallback`) avoiding memory allocations during `onDraw`.

---

## 5. Contribution Workflow for AI Agents

1. **Analyze Context**: Inspect existing code and active artifacts before proposing modifications.
2. **Execute Changes**: Apply targeted edits using built-in file tools.
3. **Build & Test**: Run `gradle_build` to guarantee zero compilation errors or lint warnings.
4. **Document**: Update the walkthrough artifact summarizing changes made.
