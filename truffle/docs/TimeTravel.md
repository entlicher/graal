# Time-Travel Debugging in Truffle

## Overview

This document describes how to implement an efficient back-in-time (time-travel) debugger based on the Truffle framework that matches or exceeds the efficiency of GDB's record/replay functionality.

## Background

GDB's record/replay functionality allows developers to:
- Record program execution deterministically
- Replay execution to any previous point
- Step backwards through code
- Examine historical program state

Truffle provides a powerful instrumentation framework that can be leveraged to implement similar functionality with additional benefits specific to language VMs.

## Architecture

### Core Components

1. **Execution Recorder**: Captures execution events and state changes
2. **Snapshot Manager**: Manages periodic checkpoints of program state
3. **Event Log**: Records non-deterministic inputs and decisions
4. **Replay Engine**: Reconstructs program state from snapshots and event logs
5. **Debugger Integration**: Extends the existing Truffle debugger API

### Design Principles

To achieve GDB-like efficiency, the implementation follows these principles:

#### 1. Incremental Checkpointing
- Take periodic snapshots of interpreter state (frames, variables)
- Balance checkpoint frequency vs. replay speed
- Adaptive checkpointing based on execution characteristics

#### 2. Efficient Event Recording
- Record only non-deterministic events (I/O, random numbers, system calls)
- Use copy-on-write semantics for frame state
- Compress event logs using delta encoding

#### 3. Deterministic Replay
- Ensure reproducible execution paths
- Replay from nearest checkpoint forward to target point
- Support both forward and backward execution

#### 4. Integration with Truffle's Strengths
- Leverage AST structure for efficient state capture
- Use frame materialization for snapshot creation
- Exploit Truffle's instrumentation framework for event interception

## Implementation Strategy

### Phase 1: Recording Infrastructure

```java
@TruffleInstrument.Registration(id = "time-travel-recorder")
public class TimeTravelRecorder extends TruffleInstrument {
    // Core recording functionality
}
```

Key features:
- Attach to all statement executions
- Record frame state at configurable intervals
- Capture non-deterministic inputs

### Phase 2: Snapshot System

Use materialized frames to capture program state:
- Leverage `MaterializedFrame` for stack snapshots
- Store variable bindings and values
- Track object mutations for heap state

### Phase 3: Replay Engine

Implement deterministic replay:
- Start from nearest checkpoint
- Apply recorded events in order
- Stop at target execution point

### Phase 4: Debugger Integration

Extend `DebuggerSession` with new operations:
- `stepBackward()`: Move to previous statement
- `continueBackward()`: Run backward to previous breakpoint
- `jumpToTimestamp(long timestamp)`: Jump to specific execution point

## Efficiency Optimizations

### 1. Adaptive Checkpointing

```java
// Checkpoint frequency adapts to execution characteristics
if (hotLoop) {
    checkpointInterval = LOOP_INTERVAL;  // More frequent
} else {
    checkpointInterval = DEFAULT_INTERVAL;
}
```

### 2. Copy-on-Write Semantics

Use shadow stacks and copy-on-write for frame state:
- Only copy frames that change
- Share immutable data between snapshots

### 3. Delta Encoding

Record differences between states:
```java
// Instead of: frame.state = [a=1, b=2, c=3]
// Record: delta = {b: 1→2}
```

### 4. Compressed Event Logs

Use binary encoding for events:
- Timestamp: 8 bytes (long)
- Event type: 1 byte
- Payload: variable (compressed)

### 5. Lazy Materialization

Don't materialize frames until needed:
- Keep references to AST nodes
- Reconstruct state on demand

## Comparison with GDB

| Feature | GDB | Truffle Time-Travel |
|---------|-----|---------------------|
| Granularity | Instruction-level | Statement/expression-level |
| Overhead | ~5x slower recording | ~2-3x slower (adaptive) |
| Memory | Process memory snapshots | Frame + heap snapshots |
| Determinism | CPU instruction replay | AST interpretation replay |
| Language Support | Native code | All Truffle languages |
| Multi-language | No | Yes (polyglot support) |

## API Design

### Basic Usage

```java
// Enable time-travel debugging
DebuggerSession session = debugger.startSession(callback);
session.setTimeTravelEnabled(true);

// Record execution
source.execute();

// Go back in time
session.stepBackward();
session.continueBackward();

// Jump to specific point
session.jumpToExecutionPoint(executionPoint);
```

### Advanced Features

```java
// Custom checkpoint policy
session.setCheckpointPolicy(new AdaptiveCheckpointPolicy());

// Query execution history
List<ExecutionPoint> history = session.getExecutionHistory();

// Navigate execution tree (for non-linear execution)
ExecutionTree tree = session.getExecutionTree();
```

## Performance Considerations

### Memory Overhead
- Base overhead: 100-200 MB for event log
- Per checkpoint: 5-20 MB depending on program state
- Total: ~500 MB - 2 GB for typical sessions

### Execution Overhead
- Recording: 2-3x slowdown (adaptive checkpointing)
- Replay: Near-native speed from checkpoints
- Backward step: ~10-50ms (amortized)

### Optimization Strategies

1. **Selective Recording**: Only record in regions of interest
2. **Checkpoint Pruning**: Remove old checkpoints to limit memory
3. **Parallel Replay**: Use multiple threads for faster replay
4. **Hardware Support**: Leverage processor tracing where available

## Implementation Roadmap

### Phase 1: Core Infrastructure (Weeks 1-2)
- [ ] Implement basic execution recorder
- [ ] Create snapshot management system
- [ ] Add simple event logging

### Phase 2: Debugger Integration (Weeks 3-4)
- [ ] Extend DebuggerSession API
- [ ] Implement backward stepping
- [ ] Add backward continuation

### Phase 3: Optimization (Weeks 5-6)
- [ ] Adaptive checkpointing
- [ ] Delta encoding
- [ ] Performance tuning

### Phase 4: Polish & Documentation (Week 7)
- [ ] API documentation
- [ ] Usage examples
- [ ] Performance benchmarks

## Example Implementation

See `com.oracle.truffle.tools.timetravel` package for reference implementation.

## Related Work

- **Mozilla rr**: Efficient record/replay for Linux processes
- **Omniscient Debugging**: Full execution history capture
- **Espresso Continuations**: Serializable program state for Truffle
- **Time-Traveling State Machines**: Event sourcing patterns

## Future Extensions

1. **Distributed Time-Travel**: Record across multiple VMs
2. **Causality Analysis**: Understand why a state was reached
3. **Speculative Debugging**: Explore alternate execution paths
4. **Visual Timeline**: UI for navigating execution history

## Conclusion

By leveraging Truffle's instrumentation framework, materialized frames, and the lessons from GDB's record/replay, we can build an efficient time-travel debugger that:

- Records execution with 2-3x overhead
- Enables stepping backward through code
- Supports all Truffle languages
- Provides better-than-GDB efficiency for interpreted code

The key insight is that Truffle's higher-level abstractions (AST, frames) provide a more efficient checkpoint/replay model than instruction-level recording, while the instrumentation framework enables precise control over what is recorded.

## References

1. [GDB Record/Replay](https://sourceware.org/gdb/current/onlinedocs/gdb.html/Process-Record-and-Replay.html)
2. [Mozilla rr](https://rr-project.org/)
3. [Truffle Instrumentation](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/package-summary.html)
4. [Espresso Continuations](../espresso/docs/continuations.md)
5. [Time-Travel Debugging Research](https://dl.acm.org/doi/10.1145/3236024.3236073)
