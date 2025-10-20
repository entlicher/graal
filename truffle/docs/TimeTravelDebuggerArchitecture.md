# Back-in-Time Debugger Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Debugger Client / User                          │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 │ Uses API
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       DebuggerSession                                │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │  Public API Methods:                                        │    │
│  │  • setTimeTravelEnabled(boolean)                           │    │
│  │  • isTimeTravelEnabled()                                   │    │
│  │  • canStepBackward() / canStepForward()                   │    │
│  │  • getCurrentSnapshot()                                    │    │
│  │  • getExecutionHistory()                                   │    │
│  │  • clearExecutionHistory()                                 │    │
│  │  • getHistoryPosition() / getHistorySize()                │    │
│  └────────────────────────────────────────────────────────────┘    │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │              TimeTravelRecorder (Internal)                  │    │
│  │  ┌──────────────────────────────────────────────────┐     │    │
│  │  │  Circular Buffer: [Snapshot₀ ... Snapshotₙ]      │     │    │
│  │  │  Current Position: Index                          │     │    │
│  │  │  Max Size: 1000 (configurable)                    │     │    │
│  │  └──────────────────────────────────────────────────┘     │    │
│  └────────────────────────────────────────────────────────────┘    │
│                                 │                                     │
│                                 │ Records                             │
│                                 ▼                                     │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │           doSuspend() Integration Point                     │    │
│  │  • Creates SuspendedEvent                                   │    │
│  │  • Calls timeTravelRecorder.recordSnapshot(event)          │    │
│  │  • Invokes user callback                                    │    │
│  └────────────────────────────────────────────────────────────┘    │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 │ Suspends on
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Guest Language Execution                          │
│  • Breakpoints                                                       │
│  • Step operations                                                   │
│  • Source locations                                                  │
└─────────────────────────────────────────────────────────────────────┘


Data Flow:
──────────

1. Execution hits suspension point
   ▼
2. DebuggerSession.doSuspend() called
   ▼
3. SuspendedEvent created with stack frames
   ▼
4. TimeTravelRecorder.recordSnapshot(event)
   ├─ Captures source location
   ├─ Captures suspend anchor
   ├─ Captures stack frames
   │  └─ For each frame:
   │     ├─ Frame name
   │     ├─ Source section
   │     └─ Variable values (as strings)
   ▼
5. Snapshot stored in circular buffer
   ▼
6. User callback invoked


TimeTravelSnapshot Structure:
─────────────────────────────

┌────────────────────────────────────┐
│      TimeTravelSnapshot            │
├────────────────────────────────────┤
│ • sequenceNumber: long             │
│ • sourceSection: SourceSection     │
│ • suspendAnchor: SuspendAnchor     │
│ • timestamp: long                  │
│ • stackFrames: List<FrameSnapshot> │
└────────────────────────────────────┘
              │
              │ contains
              ▼
┌────────────────────────────────────┐
│        FrameSnapshot               │
├────────────────────────────────────┤
│ • name: String                     │
│ • sourceSection: SourceSection     │
│ • variables: Map<String, Object>   │
└────────────────────────────────────┘


Circular Buffer Behavior:
─────────────────────────

Initial State (empty):
  Position: -1
  Buffer: []

After Recording 3 Snapshots:
  Position: 2
  Buffer: [S₀, S₁, S₂]
         ─────────▲

When Buffer is Full (size=1000):
  Position: 999
  Buffer: [S₀, S₁, ..., S₉₉₉]
                      ─────▲

When New Snapshot Arrives (buffer full):
  Position: 999
  Buffer: [S₁, S₂, ..., S₉₉₉, S₁₀₀₀]  ← S₀ removed
                           ──────▲


Navigation Example:
──────────────────

State: [S₀, S₁, S₂, S₃, S₄]
             ──▲             Position: 1

stepForward() → Position: 2
State: [S₀, S₁, S₂, S₃, S₄]
                  ──▲

stepBackward() → Position: 1  
State: [S₀, S₁, S₂, S₃, S₄]
             ──▲

stepBackward() → Position: 0
State: [S₀, S₁, S₂, S₃, S₄]
          ▲


Usage Flow Chart:
────────────────

Start
  │
  ▼
Create DebuggerSession
  │
  ▼
setTimeTravelEnabled(true)
  │
  ▼
Install Breakpoint / Step
  │
  ▼
Execute Program ──┐
  │               │
  ▼               │
Suspension Point  │
  │               │
  ▼               │
Record Snapshot   │
  │               │
  ▼               │
User Callback     │
  │               │
  ▼               │
Continue? ────────┘
  │ No
  ▼
getExecutionHistory()
  │
  ▼
Examine Snapshots
  │
  ▼
clearExecutionHistory() (optional)
  │
  ▼
End


Memory Layout:
─────────────

DebuggerSession (1 instance per session)
  │
  └─→ TimeTravelRecorder (1 instance)
        │
        └─→ LinkedList<TimeTravelSnapshot> (up to 1000)
              │
              └─→ Each TimeTravelSnapshot:
                    • ~100 bytes (metadata)
                    • List<FrameSnapshot>
                        └─→ Each FrameSnapshot:
                              • ~100 bytes (metadata)
                              • Map<String, Object> (variable values as strings)
                                  └─→ String values (~50-200 bytes each)

Estimated Memory per Snapshot: 1-5 KB (depending on stack depth)
Total Memory (1000 snapshots): 1-5 MB typical
```
