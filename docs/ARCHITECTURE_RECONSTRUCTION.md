# PokeClaw Architecture Reconstruction

This document exists to keep the next refactor wave behavior-safe.

The goal is not "rewrite everything." The goal is:

- fewer regressions
- clearer ownership boundaries
- better QA targeting
- easier future feature work
- a codebase that is closer to product-ready without drifting from current expected behavior

## Non-Negotiables

- Do **not** change product behavior unless the change is a confirmed bug fix.
- Keep the current persisted config/MMKV keys compatible unless a migration is explicitly planned and tested.
- Every refactor must declare:
  - its scope
  - its invariants
  - the QA bundle that must be rerun
- No broad rewrite across unrelated subsystems in one go.

## Current Hotspots

### 1. `ComposeChatActivity` is still too broad

It currently owns:

- chat history
- model loading
- local/cloud switching
- send routing
- task orchestration callbacks
- permission gating
- auto-return UI updates

This makes regressions easy because UI, runtime state, and task coordination are all coupled together.

### 2. Task state is split across too many places

Current task lifecycle information is spread across:

- `AppViewModel`
- `TaskOrchestrator`
- `ForegroundService`
- `FloatingCircleManager`
- `ComposeChatActivity`
- chat message state

This is why stop-flow, auto-return, and same-session restoration are historically fragile.

### 3. Accessibility and permission state is scattered

Permission truth currently crosses:

- `SettingsActivity`
- `ComposeChatActivity`
- `ClawAccessibilityService`
- `BaseTool`
- `AutoReplyManager`

This area is better than before, but it is still easy to reintroduce drift between:

- "enabled in system settings"
- "service is bound"
- "tool can safely run now"

### 4. Local model lifecycle still spans multiple layers

The current local model path touches:

- `LocalModelManager`
- `EngineHolder`
- `LocalLlmClient`
- `ComposeChatActivity`
- `LlmConfigActivity`

This is survivable, but not yet clean enough for aggressive device-compatibility work.

### 5. QA knowledge is rich but still too manual

`QA_CHECKLIST.md` has a lot of real value now, but the project still needs:

- clearer release gates
- smaller targeted rerun bundles
- stronger mapping from refactor class → required QA bundle

## Reconstruction Strategy

Use a **phased strangler approach**, not a rewrite.

Each phase should land as a small, reviewable set of commits with a matching regression bundle.

## Phase 0 — QA Gate First

Before broad refactors, freeze the test rules.

### Deliverables

- `QA_CHECKLIST.md` clearly states:
  - current coverage state
  - release gates
  - refactor regression bundles
- blocked vs fixed vs unverified is always explicit

### Exit Criteria

- no more pretending the master sheet is 100% rerun when it is not
- every future refactor can name its rerun bundle up front

## Phase 1 — Chat Runtime Extraction

### Goal

Slim `ComposeChatActivity` down so it stops directly owning every runtime concern.

### New boundary

Extract a `ChatSessionController` that owns:

- local/cloud runtime client selection
- chat send pipeline
- local model load / unload
- chat-side session state
- model status updates that belong to runtime, not raw UI

### Keep in `ComposeChatActivity`

- lifecycle wiring
- Compose bindings
- navigation / Activity-level intents
- view-state observation

### Must Preserve

- current chat vs task routing
- same visible status labels
- same session restore behavior
- same model switch UX

### Mandatory QA bundle

- `H2`, `H2-b`, `H2-c`, `H4`, `H4-b`
- `Q4-1`, `Q4-2`, `Q5-1`, `Q5-1b`
- `Q7-*`
- `LQ1-LQ13`

## Phase 2 — Task Session Store

### Goal

Create one authoritative task-session state source.

### New boundary

Introduce a `TaskSessionStore` or equivalent state holder that owns:

- idle/running/stopping/completed state
- current task id
- current task text
- current task channel/message linkage
- stop requested / safe unwind state
- auto-return intent metadata

### Current dependents that should observe instead of ad-hoc syncing

- `TaskOrchestrator`
- `AppViewModel`
- `ForegroundService`
- `FloatingCircleManager`
- `ComposeChatActivity`

### Must Preserve

- floating pill behavior
- top-bar stop behavior
- auto-return semantics
- same conversation restoration

### Mandatory QA bundle

- `F1-F6`
- `I1-I3`
- `L1`, `L3`
- `Q7-*`
- `S2`, `S3`, `S5`, `S7`, `S8`

## Phase 3 — Permission / Accessibility Coordinator

### Goal

Make permission truth explicit and shared.

### New boundary

Introduce a coordinator/repository that distinguishes:

- configured in system settings
- bound/connected right now
- safe to run tool right now
- pending return-to-app flow

### Must Preserve

- current permission prompts
- stay-in-app monitor start
- current Settings flows
- reconnect waiting behavior

### Mandatory QA bundle

- `K1-K6`
- `J4`
- `L5`, `L5-b` when external sender is available

## Phase 4 — Local Model Runtime Consolidation

### Goal

Separate model file management from model runtime management.

### New boundary

Split concerns so:

- `LocalModelManager` handles files, downloads, validation, compatibility metadata
- a dedicated runtime layer handles engine acquisition, backend fallback, live session ownership

### Why

This is the phase that makes lower-RAM support and more local models safer to add.

### Must Preserve

- GPU-first / CPU-fallback behavior
- truthful backend label
- current model selection semantics
- no regression in task/chat session handoff

### Mandatory QA bundle

- `H4`, `H4-b`
- `Q5-1`, `Q5-1b`
- `LQ1-LQ13`
- device-specific local model smoke tests

## Phase 5 — Release / Distribution Surface

### Goal

Make upgrade behavior and public release quality boring and predictable.

### Scope

- release signing path
- update checker expectations
- public upgrade documentation
- checksum / artifact consistency

### Must Preserve

- current in-app update prompt semantics
- current stable-signing direction

### Mandatory QA bundle

- `Dbg-u1-Dbg-u3`
- `Rel-s1-Rel-s7`

## What Should Not Happen

- No mega-branch that rewrites chat, task, accessibility, and models at once.
- No "cleanup" commit that also changes visible task behavior.
- No undocumented storage migration.
- No issue-thread claims that a fix is public unless it is actually in a public release.

## Decision Rule

If a proposed refactor does not make one of these easier:

- QA targeting
- ownership clarity
- behavior preservation
- device compatibility work
- future feature velocity

then it is probably not worth landing yet.
