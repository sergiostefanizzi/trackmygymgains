# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

TrackMyGymGains: an Android app (Kotlin + Jetpack Compose, Material 3) to track gym workouts. The user imports their training plan from an `.xlsx` file, starts a workout, logs each set (load, reps, **Buffer** with RIR or **Cedimento**/failure) and a rest timer starts automatically with the REST time the plan defines for that exercise. UI strings are Italian (`res/values/strings.xml`).

The work is organised in milestones (M0 setup ✅, M1 xlsx import ✅, M2 workout logging, M3 rest timer, M4 history). Each subtask is closed only when its tests pass, then committed with a concise conventional message and pushed to `origin/dev` (not `main`).

## Commands (Windows, PowerShell)

Gradle must run on Android Studio's JBR (the shell's default `JAVA_HOME` points to a Temurin 21 that is not used):

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest                      # JVM tests (JUnit4 + Robolectric)
.\gradlew.bat :app:testDebugUnitTest --tests "com.trackmygymgains.WeightFormatTest"   # single JVM test class/method
.\gradlew.bat connectedDebugAndroidTest              # instrumented tests, needs a running device
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.trackmygymgains.MainActivityTest"  # single instrumented class
```

`.\scripts\run-tests.ps1` is the full gate: forces the AS JBR, runs JVM tests, boots the AVD `Medium_Phone_API_37.0` headless if no device is connected, runs connected tests, kills the emulator it started, exits non-zero on failure. `-UnitOnly` skips device tests. Reports: `app\build\reports\tests\testDebugUnitTest\index.html`, `app\build\reports\androidTests\connected\debug\index.html`. Connected test XML file names contain spaces and ` - ` (e.g. `TEST-Medium_Phone_API_37.0(AVD) - 17.xml`) — quote them in shell commands.

## Build setup gotchas

- AGP 9.x with **built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin; only the Compose compiler plugin and KSP are applied. Versions live in `gradle/libs.versions.toml`.
- `compileSdk` is `release(37) { minorApiLevel = 1 }` (37.1) because the Compose BOM requires it; targetSdk 37, minSdk 26. Bytecode target 17 via `compileOptions` (no `jvmToolchain` — no JDK 17 is installed).
- Robolectric runs on SDK 37; `testOptions.unitTests.isIncludeAndroidResources = true` is required for resource access in JVM tests.
- Compose tests use the `androidx.compose.ui.test.junit4.v2` rule imports (v1 is deprecated in this Compose version).
- Room schemas are exported to `app/schemas/` (committed) via the KSP arg `room.schemaLocation`.
- In the Bash tool, heredocs/echo mangle backslashes in Windows paths — write files such as `local.properties` with the Write tool.

## Architecture (target design)

Single `:app` module, package `com.trackmygymgains`. Manual DI: `TrackMyGymGainsApp` exposes a lazy `AppContainer` that will own the Room database and repositories (no Hilt). Screens are Compose + ViewModel exposing `StateFlow`, wired with Navigation Compose.

Planned layers:
- `data/xlsx/` — `XlsxReader` (custom reader on `java.util.zip` + `javax.xml`, **no Apache POI**, so it runs identically in plain JVM tests) → cell grid; `WorkoutPlanParser` → days and exercises.
- `data/db/` + `data/repo/` — Room entities `WorkoutPlan`, `WorkoutDay`, `PlannedExercise`, `WorkoutSession`, `SetLog` (`outcome` BUFFER|FAILURE, `rir` only for BUFFER); `PlanRepository` (import in one transaction, new plan becomes the only active one), `SessionRepository`.
- `ui/importplan`, `ui/home`, `ui/workout`, `ui/history`.
- `timer/` — `RestTimer` based on an end timestamp with an injectable clock (survives rotation/process death) plus a foreground service for the countdown notification and end-of-rest vibration.

### Plan file format (`.xlsx`)

The reference is the **first (most recent) sheet**; older sheets in the user's file use obsolete layouts and are not supported. Only the plan is imported, not the weekly history (columns from `CARICO W1` onwards are ignored).
- A sheet holds repeated day blocks: a title row (col A: `FULL BODY 1`, …), then a header row `ESERCIZIO | SET | TARGET REP | TARGET RIR | REST | NOTE | CARICO W1 …`, then one row per exercise until col A is empty.
- Locate columns by header label, not position. SET `3.0` → 3 (tolerate `1 + 2` → 3). REST `3'` → 180 s, `30"` → 30 s, `1'30"` → 90 s, `/` or empty → null (jump set: no automatic timer). NOTE `/` → null.

## Test data and private files

`references/scheda_gym.xlsx` is the user's real plan and contains personal data: it is gitignored and must never be committed, moved or copied into the repo. Tests build synthetic xlsx files with a test-side builder that mimics the first sheet's structure. A test may read the real file only when it exists (`assumeTrue`). `local.properties` is also gitignored.
