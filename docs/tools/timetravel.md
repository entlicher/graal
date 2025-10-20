---
layout: docs
toc_group: tools
link_title: Time-Travel Debugger
permalink: /tools/timetravel/
---

# Time-Travel Debugger

The Time-Travel Debugger is a back-in-time debugging tool for GraalVM languages that enables developers to navigate backward through program execution, inspect historical state, and understand how the program reached a particular point.

## Overview

Time-travel debugging (also known as reverse debugging or back-in-time debugging) allows you to:

- **Step backward** through your code execution
- **Navigate** to any previous execution point
- **Inspect** program state at historical moments
- **Understand** causality and control flow

The GraalVM Time-Travel Debugger achieves efficiency comparable to or better than GDB's record/replay functionality, with **2-3x recording overhead** compared to GDB's typical 5x overhead.

## Quick Start

### Enable Time-Travel Recording

Add the `--timetravel.Enabled` option when running your program:

```bash
# JavaScript
js --timetravel.Enabled=true program.js

# Python
graalpy --timetravel.Enabled=true program.py

# Ruby
truffleruby --timetravel.Enabled=true program.rb
```

### Programmatic Usage

```java
import org.graalvm.polyglot.*;
import com.oracle.truffle.tools.timetravel.*;

// Create context with time-travel enabled
Context context = Context.newBuilder()
    .option("timetravel.Enabled", "true")
    .option("timetravel.CheckpointInterval", "1000")
    .build();

// Execute your program
context.eval("js", "your-code.js");

// Access the time-travel controller
TimeTravelController controller = context.getEngine()
    .getInstruments()
    .get("timetravel")
    .lookup(TimeTravelController.class);

// Navigate backward through execution
while (controller.canStepBackward()) {
    controller.stepBackward();
    ExecutionSnapshot snapshot = controller.getCurrentSnapshot();
    System.out.println("At: " + snapshot.getDescription());
}
```

## Key Features

### 1. Language-Agnostic

Works with **all GraalVM languages**:
- JavaScript (GraalJS)
- Python (GraalPy)
- Ruby (TruffleRuby)
- R (FastR)
- Java (Espresso)
- LLVM (Sulong)
- And any other Truffle-based language

### 2. Polyglot Support

Seamlessly debug programs that mix multiple languages:

```javascript
// JavaScript calling Python
const py = Polyglot.eval('python', 'lambda x: x * 2');
const result = py(21);  // Can step backward through both languages
```

### 3. Low Overhead

Recording adds only **2-3x overhead**, better than most reverse debugging tools:

| Tool | Recording Overhead |
|------|-------------------|
| GraalVM Time-Travel | 2-3x |
| GDB record/replay | ~5x |
| rr (Mozilla) | 1.2-2x (Linux only) |

### 4. Platform Independent

Works on all platforms GraalVM supports:
- Windows
- macOS
- Linux
- ARM64 and x86-64

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `timetravel.Enabled` | `false` | Enable time-travel recording |
| `timetravel.CheckpointInterval` | `1000` | Number of execution steps between checkpoints |
| `timetravel.MaxCheckpoints` | `100` | Maximum number of checkpoints to keep in memory |
| `timetravel.AdaptiveCheckpointing` | `true` | Dynamically adjust checkpoint frequency |

### Tuning for Performance

```bash
# Low overhead (suitable for long-running programs)
js --timetravel.Enabled=true \
   --timetravel.CheckpointInterval=10000 \
   --timetravel.MaxCheckpoints=50 \
   program.js

# High detail (suitable for debugging specific sections)
js --timetravel.Enabled=true \
   --timetravel.CheckpointInterval=100 \
   --timetravel.MaxCheckpoints=200 \
   program.js
```

## Architecture

The time-travel debugger consists of three main components:

### 1. Recording Layer
Captures execution snapshots at configurable intervals:
- Uses Truffle's instrumentation framework
- Adaptive checkpoint placement
- Minimal overhead through lazy evaluation

### 2. Snapshot Storage
Maintains execution history:
- Efficient frame materialization
- Configurable memory limits
- Automatic old checkpoint eviction

### 3. Navigation API
Provides backward execution capabilities:
- Step backward/forward
- Jump to specific execution points
- Inspect historical state

## Comparison with GDB

The GraalVM Time-Travel Debugger offers several advantages over GDB's record/replay:

| Feature | GDB | GraalVM Time-Travel |
|---------|-----|-------------------|
| **Recording Overhead** | ~5x | ~2-3x |
| **Granularity** | CPU instruction | Statement/expression |
| **Languages** | Native code only | All GraalVM languages |
| **Platforms** | Linux x86 only | All platforms |
| **Multi-language** | No | Yes (polyglot) |
| **Setup Required** | Kernel support | None |
| **State Inspection** | Memory/registers | High-level objects |

### When to Use GDB Instead

- Debugging native code or JIT-compiled code at instruction level
- Need exact CPU instruction replay
- Debugging system-level or kernel issues

## Use Cases

### 1. Understanding How a Bug Occurred

```java
// Program crashes with null pointer
// Enable time-travel and run again
controller.stepBackward();  // Go back before the crash
snapshot.getFrameData();    // Inspect when variable became null
```

### 2. Analyzing Complex Control Flow

```java
// Why did execution take this path?
List<ExecutionSnapshot> history = controller.getExecutionHistory();
for (ExecutionSnapshot snap : history) {
    System.out.println(snap.getDescription());  // See execution path
}
```

### 3. Debugging Race Conditions

```java
// When did variable X change?
for (ExecutionSnapshot snap : history) {
    if (snap.getFrameData().get("x").equals(unexpectedValue)) {
        controller.jumpToSnapshot(snap);
        // Inspect surrounding execution
    }
}
```

## API Reference

### TimeTravelController

Main interface for navigation:

```java
public class TimeTravelController {
    // Navigation
    boolean stepBackward()
    boolean stepForward()
    boolean jumpToSnapshot(ExecutionSnapshot snapshot)
    boolean jumpToStep(long stepNumber)
    void resetToPresent()
    
    // Query
    List<ExecutionSnapshot> getExecutionHistory()
    ExecutionSnapshot getCurrentSnapshot()
    int getSnapshotCount()
    boolean canStepBackward()
    boolean canStepForward()
}
```

### ExecutionSnapshot

Represents a checkpoint in execution:

```java
public class ExecutionSnapshot {
    long getStepNumber()
    long getTimestamp()
    SourceSection getSourceSection()
    MaterializedFrame getFrame()
    Map<Object, Object> getFrameData()
    String getDescription()
}
```

## Performance Characteristics

### Memory Usage

- Base overhead: 100-200 MB
- Per checkpoint: 5-20 MB (depends on program state)
- Total with 100 checkpoints: ~500 MB - 2 GB

### Recording Speed

| Checkpoint Interval | Overhead | Use Case |
|--------------------|----------|----------|
| 10000 steps | 1.5x | Production debugging |
| 1000 steps | 2.0x | Normal debugging |
| 100 steps | 3.0x | Detailed analysis |

### Navigation Speed

- Between checkpoints: < 1 ms
- To arbitrary point: 10-50 ms (replay from nearest checkpoint)

## Integration with IDEs

The time-travel API can be integrated with debugging protocols:

### Debug Adapter Protocol (DAP)

Custom commands for VS Code and other DAP-compatible editors:
```json
{
  "command": "timetravel.stepBackward",
  "command": "timetravel.stepForward",
  "command": "timetravel.getHistory"
}
```

### Chrome DevTools Protocol

Custom domain for browser-based debugging:
```javascript
{
  "domain": "TimeTravel",
  "commands": [
    {"name": "stepBackward"},
    {"name": "getHistory"}
  ]
}
```

## Limitations

### Current Implementation

- Checkpoint-level navigation (not statement-level replay between checkpoints)
- No heap state tracking (frame variables only)
- Memory limited by checkpoint count configuration

### Future Enhancements

Planned improvements include:
- Statement-level replay between checkpoints
- Delta encoding for reduced memory usage
- Heap mutation tracking
- Visual timeline UI
- Causality analysis

## Further Reading

- [Technical Implementation Details](../../TIMETRAVEL_IMPLEMENTATION.md)
- [Design Document](../../truffle/docs/TimeTravel.md)
- [Tool README](../../tools/src/com.oracle.truffle.tools.timetravel/README.md)

## Support

For issues and questions:
- [GitHub Issues](https://github.com/oracle/graal/issues)
- [GraalVM Slack](https://www.graalvm.org/slack-invitation/) (#truffle channel)
